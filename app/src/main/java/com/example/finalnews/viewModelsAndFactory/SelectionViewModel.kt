package com.example.finalnews.viewModelsAndFactory

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceManager
import com.example.finalnews.R
import com.example.finalnews.Sources.SourcesInfo
import com.example.finalnews.Sources.UserSettings
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.database.ktx.getValue
import com.google.firebase.ktx.Firebase

class SelectionViewModel(private val application: Application) : ViewModel() {

    var mCategoriesSelectedLv = MutableLiveData<ArrayList<String>?>()
    var mCountriesSelectedLv = MutableLiveData<ArrayList<String>?>()
    var mPublishersSelectedLv = MutableLiveData<ArrayList<String>?>()
    val mFirebaseUser = MutableLiveData<FirebaseUser?>()
    val mUserSettings = MutableLiveData<UserSettings?>()
    val newsArticlesNavigate = MutableLiveData<Boolean>()
    val snackbarMsg = MutableLiveData<String>()
    val showAD = MutableLiveData<Boolean>()
    val loadUI = MutableLiveData<Boolean>()

    private val sourceDB: DatabaseReference = Firebase.database.getReference(application.resources.getString(R.string.users_label))
    private val settingsDB: DatabaseReference = Firebase.database.getReference(application.resources.getString(R.string.users_label))
    private val applicationResource = application.resources
    private val mFirebaseAuth: FirebaseAuth = Firebase.auth
    private val authListener:FirebaseAuth.AuthStateListener
    var mInterstitialAd: InterstitialAd? = null

    init {
        authListener = FirebaseAuth.AuthStateListener {
            if(it.currentUser!=null){
                initializeSourceInfo(it.currentUser!!)
                initializeUserSettings(it.currentUser!!)
            }
            mFirebaseUser.value = it.currentUser
        }
        mFirebaseAuth.addAuthStateListener(authListener)

        loadAd()
    }

    private fun initializeSourceInfoArray(mSourceInfo:SourcesInfo?) {
        if (mSourceInfo == null) {
            mPublishersSelectedLv.value = arrayListOf()
            mCategoriesSelectedLv.value = arrayListOf()
            mCountriesSelectedLv.value = arrayListOf()
        }
        else{
            mPublishersSelectedLv.value = mSourceInfo.mPublishersSelected
            mCategoriesSelectedLv.value = mSourceInfo.mCategoriesSelected
            mCountriesSelectedLv.value = mSourceInfo.mCountriesSelected
        }
        onUILoaded()
    }

    private fun initializeUserSettings(currentUser: FirebaseUser) {
        settingsDB.child(currentUser.uid).child(application.resources.getString(R.string.settings_label)).addValueEventListener(object : ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {
                mUserSettings.value = snapshot.getValue<UserSettings>()
                val editor: SharedPreferences.Editor = PreferenceManager.getDefaultSharedPreferences(application.applicationContext).edit()
                if (mUserSettings.value != null) {
                    Log.d(SelectionViewModel::class.java.name, "valuelistener2")
                    val fontSize: String? = mUserSettings.value?.fontSize
                    val theme: String? = mUserSettings.value?.theme
                    val topHeadlinesCountry: String? = mUserSettings.value?.topHeadLinesCountry
                    editor.putString(applicationResource.getString(R.string.pref_fontSize_key), fontSize)
                    editor.putString(applicationResource.getString(R.string.theme_key), theme)
                    editor.putString(applicationResource.getString(R.string.widgetKey_top_headlines),topHeadlinesCountry)
                    editor.apply()
                }
                else {
                    val locale: String = application.applicationContext.resources.configuration.locale.country
                    val defaultCountry = locale.lowercase()
                    val newUserSettings = UserSettings(
                        applicationResource.getString(R.string.menu_medium_label),
                        applicationResource.getString(R.string.lightLabel),defaultCountry)
                    editor.putString(applicationResource.getString(R.string.pref_fontSize_key),newUserSettings.fontSize)
                    editor.putString(applicationResource.getString(R.string.theme_key),newUserSettings.theme)
                    editor.putString(applicationResource.getString(R.string.widgetKey_top_headlines),newUserSettings.topHeadLinesCountry)
                    editor.apply()
                    mFirebaseAuth.currentUser?.uid?.let { settingsDB.child(it).child(applicationResource.getString(R.string.settings_label)).setValue(newUserSettings) }
                    //settingsDB.setValue(newUserSettings)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun initializeSourceInfo(currentUser: FirebaseUser) {
        sourceDB.child(currentUser.uid).child(application.resources.getString(R.string.sources_label)).addValueEventListener(object : ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {
                val mSourceInfo = snapshot.getValue<SourcesInfo>()
                initializeSourceInfoArray(mSourceInfo)
                Log.d(SelectionViewModel::class.java.name, "valuelistener1")
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadAd() {
        InterstitialAd.load(application.applicationContext,applicationResource.getString(R.string.AdUnit_ID),
            AdRequest.Builder().build(),object : InterstitialAdLoadCallback(){
            override fun onAdFailedToLoad(adError: LoadAdError) {
                mInterstitialAd = null
            }
            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                Log.d(SelectionViewModel::class.java.name, "Ad was loaded.")
                mInterstitialAd = interstitialAd
                mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback(){
                    override fun onAdDismissedFullScreenContent() {
                        mInterstitialAd = null
                        mFirebaseAuth.currentUser?.uid?.let {
                            val mSourcesInfo = SourcesInfo(mPublishersSelectedLv.value,mCategoriesSelectedLv.value,mCountriesSelectedLv.value)
                            sourceDB.child(it).child(applicationResource.getString(R.string.sources_label)).setValue(mSourcesInfo)
                        }
                        onNewsArticlesNavigate()
                    }
                    override fun onAdShowedFullScreenContent() {}
                }
            }
        })
    }

    fun doneLabel(){
        if(mCountriesSelectedLv.value?.size==0 && mCategoriesSelectedLv.value?.size==0 && mPublishersSelectedLv.value?.size==0){
            snackbarMsg.value = "Please select news sources"
        }else if(mPublishersSelectedLv.value?.size==0){
            snackbarMsg.value = "Please select a publisher"
        }else if(mCategoriesSelectedLv.value?.size==0){
            snackbarMsg.value = "Please select a category"
        }else if(mCountriesSelectedLv.value?.size==0){
            snackbarMsg.value = "Please select a country"
        }else {
            showInterstitialAd()
        }
    }

    fun clearAllLabels(){
        mFirebaseUser.value?.uid?.let {sourceDB.child(it).child(applicationResource.getString(R.string.sources_label)).setValue(null)}
        initializeSourceInfoArray(null)
    }

    fun onPublisherClicked(item: String){
        mPublishersSelectedLv.value?.let {
            if(it.contains(item)){
                it.remove(item)
                snackbarMsg.value = "You have removed $item"
            }
            else{
                it.add(item)
                snackbarMsg.value = "You have added $item"
            }
        }
        mPublishersSelectedLv.value = mPublishersSelectedLv.value
    }

    fun onCategoryClicked(item: String){
        mCategoriesSelectedLv.value?.let {
            if(it.contains(item)){
                it.remove(item)
                snackbarMsg.value = "You have removed $item"
            }
            else{
                it.add(item)
                snackbarMsg.value = "You have added $item"
            }
        }
        mCategoriesSelectedLv.value = mCategoriesSelectedLv.value
    }

    fun onCountryClicked(item: String){
        mCountriesSelectedLv.value?.let {
            if(it.contains(item)){
                it.remove(item)
                snackbarMsg.value = "You have removed $item"
            }
            else{
                it.add(item)
                snackbarMsg.value = "You have added $item"
            }
        }
        mCountriesSelectedLv.value = mCountriesSelectedLv.value
    }

    private fun showInterstitialAd() {
        showAD.value = true
    }

    fun showInterstitialAdComplete() {
        showAD.value = false
    }

    private fun onNewsArticlesNavigate(){
        newsArticlesNavigate.value = true
    }

    fun onNewsArticlesNavigationComplete(){
        newsArticlesNavigate.value = false
    }

    fun onUILoaded(){
        loadUI.value = true
    }

    fun onUILoadComplete(){
        loadUI.value = false
    }

    fun onSnackBarShown(){
        snackbarMsg.value = ""
    }

    override fun onCleared() {
        super.onCleared()
        mFirebaseAuth.removeAuthStateListener(authListener)
    }
}