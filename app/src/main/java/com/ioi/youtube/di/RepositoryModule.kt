package com.ioi.youtube.di

import com.ioi.youtube.data.remote.NewPipeRemoteDataSource
import com.ioi.youtube.data.remote.YouTubeRemoteDataSource
import com.ioi.youtube.data.repository.CommentRepositoryImpl
import com.ioi.youtube.data.repository.ShortsRepositoryImpl
import com.ioi.youtube.data.repository.SearchRepositoryImpl
import com.ioi.youtube.data.repository.VideoRepositoryImpl
import com.ioi.youtube.domain.repository.CommentRepository
import com.ioi.youtube.domain.repository.ShortsRepository
import com.ioi.youtube.domain.repository.SearchRepository
import com.ioi.youtube.domain.repository.VideoRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindVideoRepository(impl: VideoRepositoryImpl): VideoRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds
    @Singleton
    abstract fun bindCommentRepository(impl: CommentRepositoryImpl): CommentRepository

    @Binds
    @Singleton
    abstract fun bindShortsRepository(impl: ShortsRepositoryImpl): ShortsRepository

    @Binds
    @Singleton
    abstract fun bindRemoteDataSource(impl: NewPipeRemoteDataSource): YouTubeRemoteDataSource
}
