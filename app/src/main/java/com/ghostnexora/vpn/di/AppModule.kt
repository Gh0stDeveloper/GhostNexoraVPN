package com.ghostnexora.vpn.di

import android.content.Context
import com.ghostnexora.vpn.data.local.AppDatabase
import com.ghostnexora.vpn.data.local.DataStoreManager
import com.ghostnexora.vpn.data.local.LogDao
import com.ghostnexora.vpn.data.local.ProfileDao
import com.ghostnexora.vpn.data.repository.ProfileRepository
import com.ghostnexora.vpn.security.LocalSecretCipher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = AppDatabase.getInstance(context)

    @Provides
    @Singleton
    fun provideProfileDao(db: AppDatabase): ProfileDao = db.profileDao()

    @Provides
    @Singleton
    fun provideLogDao(db: AppDatabase): LogDao = db.logDao()

    @Provides
    @Singleton
    fun provideDataStoreManager(
        @ApplicationContext context: Context
    ): DataStoreManager = DataStoreManager(context)

    @Provides
    @Singleton
    fun provideProfileRepository(
        profileDao: ProfileDao,
        logDao: LogDao,
        dataStore: DataStoreManager,
        secretCipher: LocalSecretCipher
    ): ProfileRepository = ProfileRepository(profileDao, logDao, dataStore, secretCipher)
}
