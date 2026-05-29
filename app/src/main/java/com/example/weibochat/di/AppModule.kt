package com.example.weibochat.di

import com.example.weibochat.data.DataRepository
import com.example.weibochat.data.DefaultDataRepository
import com.example.weibochat.data.WeiboApiClient
import com.example.weibochat.data.WeiboDatabase
import com.example.weibochat.ui.main.MainScreenViewModel
import com.example.weibochat.ui.weibo.WeiboTimelineViewModel
import com.example.weibochat.data.repository.AuthRepository
import com.example.weibochat.data.repository.MessageRepository
import com.example.weibochat.data.repository.TimelineRepository
import com.example.weibochat.data.repository.SettingRepository
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Database & Daos
    single { WeiboDatabase.create(get()) }
    single { get<WeiboDatabase>().messageDao() }
    single { get<WeiboDatabase>().weiboDao() }

    // Network
    single { WeiboApiClient(get()) }

    // Repository
    single { DefaultDataRepository(get(), get(), get(), get(), get()) }
    single<DataRepository> { get<DefaultDataRepository>() }
    single<AuthRepository> { get<DefaultDataRepository>() }
    single<MessageRepository> { get<DefaultDataRepository>() }
    single<TimelineRepository> { get<DefaultDataRepository>() }
    single<SettingRepository> { get<DefaultDataRepository>() }

    // ViewModels
    viewModel { MainScreenViewModel(get(), get(), get()) }
    viewModel { WeiboTimelineViewModel(get()) }
}
