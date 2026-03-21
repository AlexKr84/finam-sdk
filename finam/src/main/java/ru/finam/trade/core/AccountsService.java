package ru.finam.trade.core;

import grpc.tradeapi.v1.accounts.AccountsServiceGrpc;
import grpc.tradeapi.v1.accounts.GetAccountRequest;
import grpc.tradeapi.v1.accounts.GetAccountResponse;

import javax.annotation.Nonnull;

public class AccountsService extends CommonAuthorizedService {
    private final AccountsServiceGrpc.AccountsServiceStub accountsServiceStub;
    private final AccountsServiceGrpc.AccountsServiceBlockingStub accountsServiceBlockingStub;

    public AccountsService(TokenStorage tokenStorage, AccountsServiceGrpc.AccountsServiceStub accountsServiceStub, AccountsServiceGrpc.AccountsServiceBlockingStub accountsServiceBlockingStub) {
        super(tokenStorage);
        this.accountsServiceStub = accountsServiceStub;
        this.accountsServiceBlockingStub = accountsServiceBlockingStub;
    }

    public AccountsServiceGrpc.AccountsServiceStub getAccountsServiceStub() {
        return withAuthorized(accountsServiceStub);
    }

    public AccountsServiceGrpc.AccountsServiceBlockingStub getAccountsServiceBlockingStub() {
        return withAuthorized(accountsServiceBlockingStub);
    }

    public GetAccountResponse getAccount(@Nonnull String accountId) {
        return getAccountsServiceBlockingStub().getAccount(
                GetAccountRequest.newBuilder()
                        .setAccountId(accountId)
                        .build());
    }
}
