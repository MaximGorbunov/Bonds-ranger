package cf.mgorbunov.utils

import io.grpc.CallCredentials
import io.grpc.Metadata
import java.util.concurrent.Executor

class TokenCallCredentials(
    private val token: String
) : CallCredentials() {
    override fun applyRequestMetadata(
        requestInfo: RequestInfo,
        appExecutor: Executor,
        applier: MetadataApplier
    ) {
        appExecutor.execute {
            val metadata = Metadata()
            val authKey =
                Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER)
            metadata.put(
                authKey,
                "Bearer $token"
            )
            applier.apply(metadata)
        }
    }

    override fun thisUsesUnstableApi() {
    }
}