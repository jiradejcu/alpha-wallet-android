package com.alphawallet.app.repository;

import com.alphawallet.app.entity.Wallet;
import com.alphawallet.app.service.AccountKeystoreService;
import com.alphawallet.app.service.KeyService;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.realm.Realm;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import timber.log.Timber;

public class WalletRepository implements WalletRepositoryType
{
	private final PreferenceRepositoryType preferenceRepositoryType;
	private final AccountKeystoreService accountKeystoreService;
	private final EthereumNetworkRepositoryType networkRepository;
	private final WalletDataRealmSource walletDataRealmSource;
	private final KeyService keyService;
    private final OkHttpClient httpClient;
    private final Gson gson;
    public static final String MERCHANT_API = "http://128.199.221.49:3000";
    private static Map<String, String> merchantMap;

	public WalletRepository(PreferenceRepositoryType preferenceRepositoryType, AccountKeystoreService accountKeystoreService, EthereumNetworkRepositoryType networkRepository, WalletDataRealmSource walletDataRealmSource, KeyService keyService, OkHttpClient httpClient, Gson gson)
	{
		this.preferenceRepositoryType = preferenceRepositoryType;
		this.accountKeystoreService = accountKeystoreService;
		this.networkRepository = networkRepository;
		this.walletDataRealmSource = walletDataRealmSource;
		this.keyService = keyService;
        this.httpClient = httpClient;
        this.gson = gson;
	}

	@Override
	public Single<Wallet[]> fetchWallets()
	{
        return accountKeystoreService.fetchAccounts()
				.flatMap(wallets -> walletDataRealmSource.populateWalletData(wallets, keyService))
				.map(wallets -> {
					if (preferenceRepositoryType.getCurrentWalletAddress() == null && wallets.length > 0)
					{
						preferenceRepositoryType.setCurrentWalletAddress(wallets[0].address);
					}
					return wallets;
				});
	}

	@Override
	public Single<Wallet> findWallet(String address)
	{
		return fetchWallets()
				.flatMap(wallets -> {
					if (wallets.length == 0) return Single.error(new NoWallets("No wallets"));
					Wallet firstWallet = null;
					for (Wallet wallet : wallets)
					{
						if (address == null || wallet.sameAddress(address))
						{
							return Single.just(wallet);
						}
						if (firstWallet == null) firstWallet = wallet;
					}

					return Single.just(firstWallet);
				});
	}

    public Single<Map<String, String>> fetchMerchants()
    {
        return Single.fromCallable(() -> {
            Request request = new Request.Builder()
                    .url(MERCHANT_API + "/address")
                    .get()
                    .build();
            try
            {
                okhttp3.Response response = httpClient.newCall(request).execute();
                merchantMap = gson.fromJson(response.body().string(), Map.class);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            return merchantMap;
        });
    }

    public static String findWalletAddressFromTelNo(String telNo)
    {
        if (merchantMap.containsKey(telNo))
        {
            Timber.i("Found Wallet Address: " + merchantMap.get(telNo) + " from Telephone No." + telNo);
            return merchantMap.get(telNo);
        }
        return null;
    }

	@Override
	public Single<Wallet> createWallet(String password)
	{
		return accountKeystoreService.createAccount(password);
	}

	@Override
	public Single<Wallet> importKeystoreToWallet(String store, String password, String newPassword)
	{
		return accountKeystoreService.importKeystore(store, password, newPassword);
	}

	@Override
	public Single<Wallet> importPrivateKeyToWallet(String privateKey, String newPassword)
	{
		return accountKeystoreService.importPrivateKey(privateKey, newPassword);
	}

	@Override
	public Single<String> exportWallet(Wallet wallet, String password, String newPassword)
	{
		return accountKeystoreService.exportAccount(wallet, password, newPassword);
	}

	@Override
	public Completable deleteWallet(String address, String password)
	{
		return accountKeystoreService.deleteAccount(address, password);
	}

	@Override
	public Single<Wallet> deleteWalletFromRealm(Wallet wallet)
	{
		return walletDataRealmSource.deleteWallet(wallet);
	}

	@Override
	public Completable setDefaultWallet(Wallet wallet)
	{
		return Completable.fromAction(() -> preferenceRepositoryType.setCurrentWalletAddress(wallet.address));
	}

	@Override
	public void updateBackupTime(String walletAddr)
	{
		walletDataRealmSource.updateBackupTime(walletAddr);
	}

	@Override
	public void updateWarningTime(String walletAddr)
	{
		walletDataRealmSource.updateWarningTime(walletAddr);
	}

	@Override
	public Single<Boolean> getWalletBackupWarning(String walletAddr)
	{
		return walletDataRealmSource.getWalletBackupWarning(walletAddr);
	}

	@Override
	public Single<String> getWalletRequiresBackup(String walletAddr)
	{
		return walletDataRealmSource.getWalletRequiresBackup(walletAddr);
	}

	@Override
	public void setIsDismissed(String walletAddr, boolean isDismissed)
	{
		walletDataRealmSource.setIsDismissed(walletAddr, isDismissed);
	}

	@Override
	public Single<Wallet> getDefaultWallet()
	{
		return Single.fromCallable(preferenceRepositoryType::getCurrentWalletAddress)
				.flatMap(this::findWallet);
	}

	@Override
	public Single<Wallet[]> storeWallets(Wallet[] wallets)
	{
		return walletDataRealmSource.storeWallets(wallets);
	}

	@Override
	public Single<Wallet> storeWallet(Wallet wallet)
	{
		return walletDataRealmSource.storeWallet(wallet);
	}

	@Override
	public void updateWalletData(Wallet wallet, Realm.Transaction.OnSuccess onSuccess)
	{
		walletDataRealmSource.updateWalletData(wallet, onSuccess);
	}

	@Override
	public void updateWalletItem(Wallet wallet, WalletItem item, Realm.Transaction.OnSuccess onSuccess)
	{
		walletDataRealmSource.updateWalletItem(wallet, item, onSuccess);
	}

	@Override
	public Single<String> getName(String address)
	{
		return walletDataRealmSource.getName(address);
	}

	@Override
    public boolean keystoreExists(String address)
    {
		Wallet[] wallets = fetchWallets().blockingGet();
		for (Wallet w : wallets)
		{
			if (w.sameAddress(address)) return true;
		}
		return false;
    }

    @Override
	public Realm getWalletRealm()
	{
		return walletDataRealmSource.getWalletRealm();
	}
}
