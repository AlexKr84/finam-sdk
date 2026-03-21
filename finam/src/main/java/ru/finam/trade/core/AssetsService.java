package ru.finam.trade.core;

import grpc.tradeapi.v1.assets.AssetsServiceGrpc;
import grpc.tradeapi.v1.assets.GetAssetRequest;
import grpc.tradeapi.v1.assets.GetAssetResponse;

import javax.annotation.Nonnull;


public class AssetsService extends CommonAuthorizedService {
    private final AssetsServiceGrpc.AssetsServiceStub assetsServiceStub;
    private final AssetsServiceGrpc.AssetsServiceBlockingStub assetsServiceBlockingStub;

    public AssetsService(TokenStorage tokenStorage, AssetsServiceGrpc.AssetsServiceStub assetsServiceStub, AssetsServiceGrpc.AssetsServiceBlockingStub assetsServiceBlockingStub) {
        super(tokenStorage);
        this.assetsServiceStub = assetsServiceStub;
        this.assetsServiceBlockingStub = assetsServiceBlockingStub;
    }

    public AssetsServiceGrpc.AssetsServiceStub getAssetsServiceStub() {
        return withAuthorized(assetsServiceStub);
    }

    public AssetsServiceGrpc.AssetsServiceBlockingStub getAssetsServiceBlockingStub() {
        return withAuthorized(assetsServiceBlockingStub);
    }

    public GetAssetResponse getAsset(@Nonnull String symbol, @Nonnull String accountId) {
        return getAssetsServiceBlockingStub().getAsset(
                GetAssetRequest.newBuilder()
                        .setSymbol(symbol)
                        .setAccountId(accountId)
                        .build());
    }
}
