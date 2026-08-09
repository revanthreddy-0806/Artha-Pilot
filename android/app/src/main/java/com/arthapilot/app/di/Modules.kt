package com.arthapilot.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.arthapilot.app.BuildConfig
import com.arthapilot.app.data.local.ArthaDatabase
import com.arthapilot.app.data.local.MIGRATION_1_2
import com.arthapilot.app.data.local.PassphraseProvider
import com.arthapilot.app.data.local.PendingChangeDao
import com.arthapilot.app.data.local.StatementDao
import com.arthapilot.app.data.local.TokenStore
import com.arthapilot.app.data.local.TransactionDao
import com.arthapilot.app.data.remote.ArthaApi
import com.arthapilot.app.data.remote.AuthInterceptor
import com.arthapilot.app.data.remote.TokenAuthenticator
import com.arthapilot.app.data.repository.AuthRepository
import com.arthapilot.app.data.repository.AuthRepositoryImpl
import com.arthapilot.app.data.repository.AssistantRepository
import com.arthapilot.app.data.repository.AssistantRepositoryImpl
import com.arthapilot.app.data.repository.BudgetRepository
import com.arthapilot.app.data.repository.BudgetRepositoryImpl
import com.arthapilot.app.data.repository.InsightsRepository
import com.arthapilot.app.data.repository.InsightsRepositoryImpl
import com.arthapilot.app.data.repository.RecommendationRepository
import com.arthapilot.app.data.repository.RecommendationRepositoryImpl
import com.arthapilot.app.data.repository.StatementRepository
import com.arthapilot.app.data.repository.StatementRepositoryImpl
import com.arthapilot.app.data.repository.TransactionRepository
import com.arthapilot.app.data.repository.TransactionRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(tokenStore: TokenStore): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        return OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore))
            .authenticator(TokenAuthenticator(tokenStore, BuildConfig.API_BASE_URL))
            .addInterceptor(logging)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideArthaApi(client: OkHttpClient): ArthaApi =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ArthaApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("artha_prefs") },
        )

    @Provides
    @Singleton
    fun provideTokenStore(dataStore: DataStore<Preferences>): TokenStore = TokenStore(dataStore)
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providePassphrase(passphraseProvider: PassphraseProvider): ByteArray =
        passphraseProvider.blockingGetOrCreate()

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        passphrase: ByteArray,
    ): ArthaDatabase {
        // Load the bundled SQLCipher native library before any connection opens.
        // sqlcipher-android 4.17 has no loadLibs API; JNI_OnLoad registers all
        // natives (incl. SQLiteConnection.nativeOpen) at dlopen time.
        System.loadLibrary("sqlcipher")
        return Room.databaseBuilder(context, ArthaDatabase::class.java, "artha_pilot.db")
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    @Provides
    fun provideTransactionDao(db: ArthaDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideStatementDao(db: ArthaDatabase): StatementDao = db.statementDao()

    @Provides
    fun providePendingChangeDao(db: ArthaDatabase): PendingChangeDao = db.pendingChangeDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    abstract fun bindAssistantRepository(impl: AssistantRepositoryImpl): AssistantRepository

    @Binds
    abstract fun bindTransactionRepository(impl: TransactionRepositoryImpl): TransactionRepository

    @Binds
    abstract fun bindStatementRepository(impl: StatementRepositoryImpl): StatementRepository

    @Binds
    abstract fun bindInsightsRepository(impl: InsightsRepositoryImpl): InsightsRepository

    @Binds
    abstract fun bindBudgetRepository(impl: BudgetRepositoryImpl): BudgetRepository

    @Binds
    abstract fun bindRecommendationRepository(impl: RecommendationRepositoryImpl): RecommendationRepository
}
