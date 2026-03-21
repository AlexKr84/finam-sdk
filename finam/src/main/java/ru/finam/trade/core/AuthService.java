package ru.finam.trade.core;

import grpc.tradeapi.v1.auth.AuthRequest;
import grpc.tradeapi.v1.auth.AuthServiceGrpc;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nonnull;

@RequiredArgsConstructor
@Getter
public class AuthService {
    private final AuthServiceGrpc.AuthServiceStub authServiceStub;
    private final AuthServiceGrpc.AuthServiceBlockingStub authServiceBlockingStub;

    public String auth(@Nonnull String secret) {
        return authServiceBlockingStub.auth(
                        AuthRequest.newBuilder()
                                .setSecret(secret)
                                .build())
                .getToken();
    }
}
