package br.com.vanep.notification.repository;

import br.com.vanep.notification.model.DeviceTokenModel;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DeviceTokenRepository extends JpaRepository<DeviceTokenModel, Long> {

  Optional<DeviceTokenModel> findByFcmToken(String fcmToken);

  Optional<DeviceTokenModel> findByUserIdAndFcmToken(Long userId, String fcmToken);

  @Query("select device.fcmToken from DeviceTokenModel device where device.userId = :userId")
  List<String> findFcmTokensByUserId(Long userId);

  List<DeviceTokenModel> findByFcmTokenIn(Collection<String> fcmTokens);
}
