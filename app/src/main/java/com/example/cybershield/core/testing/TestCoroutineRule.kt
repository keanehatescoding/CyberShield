//package com.example.cybershield.core.testing
//
//import android.util.EventLogTags
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.ExperimentalCoroutinesApi
//
//@OptIn(ExperimentalCoroutinesApi::class)
//class TestCoroutineRule(
//    val dispatcher: TestDispatcher = StandardTestDispatcher(),
//) : TestWatcher() {
//
//    override fun starting(description: EventLogTags.Description) {
//        Dispatchers.setMain(dispatcher)
//    }
//
//    override fun finished(description: EventLogTags.Description) {
//        Dispatchers.resetMain()
//    }
//}