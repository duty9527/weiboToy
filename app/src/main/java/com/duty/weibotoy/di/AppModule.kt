package com.duty.weibotoy.di

import com.duty.weibotoy.data.DataRepository
import com.duty.weibotoy.data.DefaultDataRepository
import com.duty.weibotoy.data.WeiboApiClient
import com.duty.weibotoy.data.WeiboDatabase
import com.duty.weibotoy.ui.main.MainScreenViewModel
import com.duty.weibotoy.ui.weibo.WeiboTimelineViewModel
import com.duty.weibotoy.data.repository.AuthRepository
import com.duty.weibotoy.data.repository.MessageRepository
import com.duty.weibotoy.data.repository.TimelineRepository
import com.duty.weibotoy.data.repository.SettingRepository
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
