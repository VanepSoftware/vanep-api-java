package br.com.vanep.notification.service;

import br.com.vanep.notification.dto.DeviceTokenRegisterRequestDTO;
import br.com.vanep.notification.model.DeviceTokenModel;
import br.com.vanep.notification.repository.DeviceTokenRepository;
import br.com.vanep.user.model.UserModel;
import br.com.vanep.user.service.UserService;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DeviceTokenService {

  private final DeviceTokenRepository devices;
  private final UserService users;
  private final Clock clock;
  private final TransactionTemplate transaction;

  public DeviceTokenService(
      DeviceTokenRepository devices,
      UserService users,
      Clock clock,
      TransactionTemplate transaction) {
    this.devices = devices;
    this.users = users;
    this.clock = clock;
    this.transaction = transaction;
  }

  // A device belongs to whoever signed in on it last: keeping the previous owner would push
  // their notifications to someone else's phone.
  public void register(String callerUid, DeviceTokenRegisterRequestDTO request) {
    try {
      transaction.executeWithoutResult(status -> upsert(callerUid, request));
    } catch (DataIntegrityViolationException concurrentInsert) {
      // Another request inserted the same token between this lookup and insert; it exists now,
      // so the retry updates it.
      transaction.executeWithoutResult(status -> upsert(callerUid, request));
    }
  }

  void upsert(String callerUid, DeviceTokenRegisterRequestDTO request) {
    UserModel caller = users.requireByToken(callerUid);
    DeviceTokenModel device =
        devices.findByFcmToken(request.fcmToken()).orElseGet(() -> newDevice(request.fcmToken()));
    device.setUserId(caller.getId());
    device.setPlatform(request.platform());
    device.setLastSeenAt(Instant.now(clock));
    devices.saveAndFlush(device);
  }

  DeviceTokenModel newDevice(String fcmToken) {
    DeviceTokenModel device = new DeviceTokenModel();
    device.setFcmToken(fcmToken);
    return device;
  }

  @Transactional
  public void unregister(String callerUid, String fcmToken) {
    UserModel caller = users.requireByToken(callerUid);
    devices.findByUserIdAndFcmToken(caller.getId(), fcmToken).ifPresent(devices::delete);
  }

  @Transactional(readOnly = true)
  public List<String> findFcmTokensOf(Long userId) {
    return devices.findFcmTokensByUserId(userId);
  }

  @Transactional
  public void removeAll(Collection<String> fcmTokens) {
    if (fcmTokens.isEmpty()) {
      return;
    }
    devices.deleteAll(devices.findByFcmTokenIn(fcmTokens));
  }
}
