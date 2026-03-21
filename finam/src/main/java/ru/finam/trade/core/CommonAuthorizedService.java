package ru.finam.trade.core;

import io.grpc.Metadata;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.MetadataUtils;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nonnull;

@RequiredArgsConstructor
public class CommonAuthorizedService {
    private final TokenStorage tokenStorage;

    protected  <T extends AbstractStub<T>> T withAuthorized(T abstractStub) {
        var headers = new Metadata();
        addAuthHeader(headers, tokenStorage.getToken());
        return abstractStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    private static void addAuthHeader(@Nonnull Metadata metadata, @Nonnull String token) {
        var authKey = Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
        metadata.put(authKey, token);
    }
}
