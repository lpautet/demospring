package net.pautet.softs.demospring.identity;

import net.pautet.softs.demospring.identity.internal.persistence.NetatmoConnection;
import net.pautet.softs.demospring.identity.internal.persistence.NetatmoConnectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NetatmoCredentialService {

    private final NetatmoConnectionRepository connections;

    public NetatmoCredentialService(NetatmoConnectionRepository connections) {
        this.connections = connections;
    }

    @Transactional(readOnly = true)
    public NetatmoCredentials findSystemCredentials() {
        return connections.findFirstByUserIsNullAndPurpose(NetatmoConnection.Purpose.SYSTEM_DATA_CLOUD)
                .map(NetatmoCredentialService::toCredentials)
                .orElse(null);
    }

    @Transactional
    public void saveSystemCredentials(NetatmoCredentials credentials) {
        NetatmoConnection connection = connections
                .findFirstByUserIsNullAndPurpose(NetatmoConnection.Purpose.SYSTEM_DATA_CLOUD)
                .orElseGet(NetatmoConnection::new);
        connection.setPurpose(NetatmoConnection.Purpose.SYSTEM_DATA_CLOUD);
        copy(credentials, connection);
        connections.save(connection);
    }

    private static NetatmoCredentials toCredentials(NetatmoConnection connection) {
        return new NetatmoCredentials(connection.getAccessToken(), connection.getRefreshToken(),
                connection.getExpiresAt(), connection.getRefreshedAt());
    }

    static void copy(NetatmoCredentials source, NetatmoConnection target) {
        target.setAccessToken(source.accessToken());
        target.setRefreshToken(source.refreshToken());
        target.setExpiresAt(source.expiresAt());
        target.setRefreshedAt(source.refreshedAt());
    }
}
