package com.zaaam.vmusix.di

import android.content.Context
import androidx.room.Room
import com.zaaam.vmusix.data.local.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun db(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "vmusix.db")
            // Hanya untuk downgrade (mis. revert ke build lama). Untuk upgrade
            // tidak ada fallback — lihat AppDatabase.
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
}
