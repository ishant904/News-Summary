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

    var mSourceInfo = MutableLiveData<SourcesInfo?>()
    var mCategoriesSelectedLv = MutableLiveData<ArrayList<String>?>()
    var mCountriesSelectedLv = MutableLiveData<ArrayList<String>?>()
    var mPublishersSelectedLv = MutableLiveData<ArrayList<String>?>()
    val mFirebaseUser = MutableLiveData<FirebaseUser?>()
    val mUserSettings = MutableLiveData<UserSettings?>()
    val newsArticlesNavigate = MutableLiveData<Boolean>()
    val snackbarMsg = MutableLiveData<String>()
    val showAD = MutableLiveData<Boolean>()
    val loadUI = MutableLiveData<Boolean>()

    private var sourceDB: DatabaseReference = Firebase.database.getReference(application.resources.getString(R.string.users_label))
    private var settingsDB: DatabaseReference = Firebase.database.getReference(application.resources.getString(R.string.users_label))
    private val applicationResource = application.resources
    private var mFirebaseAuth: FirebaseAuth = Firebase.auth
    private var authListener:FirebaseAuth.AuthStateListener
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

    private fun initializeSourceInfoArray() {
        onUILoaded()
        mCategoriesSelectedLv.value = mSourceInfo.value?.mCategoriesSelected
        mCountriesSelectedLv.value = mSourceInfo.value?.mCountriesSelected
        mPublishersSelectedLv.value = mSourceInfo.value?.mPublishersSelected
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
                mSourceInfo.value = snapshot.getValue<SourcesInfo>()
                if (mSourceInfo.value == null) {
                    val publishersSelected = arrayListOf<String>()
                    val categoriesSelected = arrayListOf<String>()
                    val countriesSelected = arrayListOf<String>()
                    mSourceInfo.value = SourcesInfo(publishersSelected,categoriesSelected,countriesSelected)
                }
                Log.d(SelectionViewModel::class.java.name, "valuelistener1")

                initializeSourceInfoArray()
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
                            sourceDB.child(it).child(applicationResource.getString(R.string.sources_label)).setValue(mSourceInfo.value)
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

    override fun onCleared() {
        super.onCleared()
        mFirebaseAuth.removeAuthStateListener(authListener)
    }
}