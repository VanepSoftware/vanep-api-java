package br.com.vanep.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.vanep.notification.dto.DeviceTokenRegisterRequestDTO;
import br.com.vanep.notification.enums.DevicePlatform;
import br.com.vanep.notification.model.DeviceTokenModel;
import br.com.vanep.notification.repository.DeviceTokenRepository;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");

  @Mock private DeviceTokenRepository devices;
  @Mock private UserService users;
  @Mock private TransactionTemplate transaction;
  @Captor private ArgumentCaptor<DeviceTokenModel> saved;

  private DeviceTokenService service;
  private UserModel maria;

  @BeforeEach
  void setUp() {
    lenient()
        .doAnswer(
            call -> {
              call.<Consumer<TransactionStatus>>getArgument(0).accept(null);
              return null;
            })
        .when(transaction)
        .executeWithoutResult(any());
    service = new DeviceTokenService(devices, users, Clock.fixed(NOW, ZoneOffset.UTC), transaction);
    maria = user(1L, "maria-uid");
  }

  @Test
  void registersANewDeviceForTheCaller() {
    when(users.requireByToken("maria-uid")).thenReturn(maria);
    when(devices.findByFcmToken("fcm-1")).thenReturn(Optional.empty());

    service.register("maria-uid", new DeviceTokenRegisterRequestDTO("fcm-1", DevicePlatform.IOS));

    verify(devices).saveAndFlush(saved.capture());
    DeviceTokenModel device = saved.getValue();
    assertThat(device.getUserId()).isEqualTo(1L);
    assertThat(device.getFcmToken()).isEqualTo("fcm-1");
    assertThat(device.getPlatform()).isEqualTo(DevicePlatform.IOS);
    assertThat(device.getLastSeenAt()).isEqualTo(NOW);
  }

  @Test
  void registeringAKnownDeviceAgainOnlyRefreshesIt() {
    DeviceTokenModel known = device(maria, "fcm-1", DevicePlatform.ANDROID);
    when(users.requireByToken("maria-uid")).thenReturn(maria);
    when(devices.findByFcmToken("fcm-1")).thenReturn(Optional.of(known));

    service.register(
        "maria-uid", new DeviceTokenRegisterRequestDTO("fcm-1", DevicePlatform.ANDROID));

    verify(devices).saveAndFlush(known);
    assertThat(known.getUserId()).isEqualTo(1L);
    assertThat(known.getLastSeenAt()).isEqualTo(NOW);
  }

  @Test
  void aDeviceSignedInByAnotherUserMovesToThem() {
    UserModel bruno = user(2L, "bruno-uid");
    DeviceTokenModel marias = device(maria, "fcm-1", DevicePlatform.ANDROID);
    when(users.requireByToken("bruno-uid")).thenReturn(bruno);
    when(devices.findByFcmToken("fcm-1")).thenReturn(Optional.of(marias));

    service.register(
        "bruno-uid", new DeviceTokenRegisterRequestDTO("fcm-1", DevicePlatform.ANDROID));

    verify(devices).saveAndFlush(marias);
    assertThat(marias.getUserId()).isEqualTo(2L);
  }

  @Test
  void losingTheInsertRaceRetriesAsAnUpdate() {
    DeviceTokenModel insertedMeanwhile = device(maria, "fcm-1", DevicePlatform.ANDROID);
    when(users.requireByToken("maria-uid")).thenReturn(maria);
    when(devices.findByFcmToken("fcm-1"))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(insertedMeanwhile));
    when(devices.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("device_token_fcm_token_active_key"))
        .thenAnswer(call -> call.getArgument(0));

    service.register("maria-uid", new DeviceTokenRegisterRequestDTO("fcm-1", DevicePlatform.IOS));

    verify(devices).saveAndFlush(insertedMeanwhile);
    assertThat(insertedMeanwhile.getPlatform()).isEqualTo(DevicePlatform.IOS);
  }

  @Test
  void unregistersOnlyTheCallersOwnDevice() {
    DeviceTokenModel marias = device(maria, "fcm-1", DevicePlatform.ANDROID);
    when(users.requireByToken("maria-uid")).thenReturn(maria);
    when(devices.findByUserIdAndFcmToken(1L, "fcm-1")).thenReturn(Optional.of(marias));

    service.unregister("maria-uid", "fcm-1");

    verify(devices).delete(marias);
  }

  @Test
  void unregisteringADeviceTheCallerDoesNotOwnChangesNothing() {
    when(users.requireByToken("maria-uid")).thenReturn(maria);
    when(devices.findByUserIdAndFcmToken(1L, "someone-elses")).thenReturn(Optional.empty());

    service.unregister("maria-uid", "someone-elses");

    verify(devices, never()).delete(any());
  }

  @Test
  void findsTheFcmTokensOfAUser() {
    when(devices.findFcmTokensByUserId(1L)).thenReturn(List.of("fcm-1", "fcm-2"));

    assertThat(service.findFcmTokensOf(1L)).containsExactly("fcm-1", "fcm-2");
  }

  @Test
  void removesTheGivenTokens() {
    List<DeviceTokenModel> stale = List.of(device(maria, "fcm-1", DevicePlatform.IOS));
    when(devices.findByFcmTokenIn(List.of("fcm-1"))).thenReturn(stale);

    service.removeAll(List.of("fcm-1"));

    verify(devices).deleteAll(stale);
  }

  @Test
  void removingNoTokensSkipsTheDatabase() {
    service.removeAll(List.of());

    verifyNoInteractions(devices);
  }

  private UserModel user(Long id, String uid) {
    UserModel user = new UserModel();
    user.setId(id);
    user.setToken(uid);
    return user;
  }

  private DeviceTokenModel device(UserModel owner, String fcmToken, DevicePlatform platform) {
    DeviceTokenModel device = new DeviceTokenModel();
    device.setUserId(owner.getId());
    device.setFcmToken(fcmToken);
    device.setPlatform(platform);
    return device;
  }
}
