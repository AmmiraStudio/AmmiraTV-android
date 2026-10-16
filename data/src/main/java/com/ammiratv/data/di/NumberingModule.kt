package com.ammiratv.data.di

import android.content.Context
import com.ammiratv.core.numbering.ChannelNumberingRepository
import com.ammiratv.core.numbering.FileChannelNumberingRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NumberingBindings {
    @Binds
    abstract fun bindManualNumbers(
        impl: com.ammiratv.data.repository.ManualNumberRepositoryImpl
    ): com.ammiratv.domain.repository.ManualNumberRepository

    companion object {
        @Provides
        @Singleton
        fun provideNumberingRepository(@ApplicationContext context: Context): ChannelNumberingRepository =
            FileChannelNumberingRepository(context)
    }
}
