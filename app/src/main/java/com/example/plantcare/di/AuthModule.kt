package com.example.plantcare.di

import android.content.Context
import com.example.plantcare.data.auth.FirebaseAuthRepositoryImpl
import com.example.plantcare.data.auth.FirebaseAuthService
import com.example.plantcare.data.local.AppDatabase
import com.example.plantcare.data.session.SessionManager
import com.example.plantcare.domain.repository.AuthRepository
import com.example.plantcare.data.remote.PlantRemoteDataSource
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides
    @Singleton
    fun provideSessionManager(@ApplicationContext context: Context): SessionManager = SessionManager(context)

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseAuthService(auth: FirebaseAuth): FirebaseAuthService = FirebaseAuthService(auth)

    @Provides
    @Singleton
    fun provideGoogleClient(@ApplicationContext context: Context): GoogleSignInClient {
        val serverClientId = context.getString(com.example.plantcare.R.string.default_web_client_id)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(serverClientId)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(
        db: AppDatabase,
        service: FirebaseAuthService,
        sessionManager: SessionManager,
        googleClient: GoogleSignInClient,
        plantRemoteDataSource: PlantRemoteDataSource
    ): AuthRepository = FirebaseAuthRepositoryImpl(
        db,
        service,
        sessionManager,
        googleClient,
        plantRemoteDataSource
    )
}


