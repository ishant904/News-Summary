package com.example.finalnews

import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.viewpager.widget.ViewPager
import com.example.finalnews.Adapters.SectionsPageAdapter
import com.example.finalnews.Fragments.CategoriesSlidePageFragment
import com.example.finalnews.Fragments.CountriesSlidePageFragment
import com.example.finalnews.Fragments.PublishersSlidePageFragment
import com.example.finalnews.Sources.SourcesInfo
import com.example.finalnews.Sources.UserSettings
import com.firebase.ui.auth.AuthUI
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuth.AuthStateListener
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.database.ktx.getValue
import com.google.firebase.ktx.Firebase
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(),PublishersSlidePageFragment.OnItemClickListener,CategoriesSlidePageFragment.OnItemClickListener, CountriesSlidePageFragment.OnItemClickListener,SharedPreferences.OnSharedPreferenceChangeListener {

    companion object{
        private const val RC_SIGN_IN = 1
        const val SOURCES = "sources"
        var mFirebaseSourcesInfo:SourcesInfo? = null
        fun getSourceInfo():SourcesInfo? = mFirebaseSourcesInfo
        var TAG = "MainActivity"
    }
    private lateinit var mFirebaseAuth: FirebaseAuth
    private lateinit var sourceDB: DatabaseReference
    private lateinit var settingsDB: DatabaseReference
    private var mInterstitialAd: InterstitialAd? = null
    var mUserSettings:UserSettings? = null
    private lateinit var pref: SharedPreferences
    private lateinit var mAuthStateListener: AuthStateListener
    private var mSavedInstanceState:Bundle? = null
    private lateinit var mValueEventListener1: ValueEventListener
    private lateinit var mValueEventListener2: ValueEventListener

    override fun onCreate(savedInstanceState: Bundle?) {
        pref = PreferenceManager.getDefaultSharedPreferences(this)
        pref.registerOnSharedPreferenceChangeListener(this)
        val themeName = pref.getString(resources.getString(R.string.theme_key), resources.getString(R.string.theme_default))
        if(themeName?.equals(resources.getString(R.string.lightLabel)) == true){
            setTheme(R.style.LightTheme)
        } else if (themeName?.equals(resources.getString(R.string.darkLabel)) == true) {
            setTheme(R.style.DarkTheme)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        Log.d(TAG,"oncreate")
        sourceDB = Firebase.database.getReference(getString(R.string.users_label))
        settingsDB = Firebase.database.getReference(resources.getString(R.string.users_label))
        mFirebaseAuth = Firebase.auth
        if(savedInstanceState!=null){
            if(savedInstanceState.containsKey(SOURCES)){
                Log.d(TAG, "restore onsave")
                mFirebaseSourcesInfo = savedInstanceState.getParcelable(SOURCES)
                updateUI(savedInstanceState,mFirebaseSourcesInfo)
                mSavedInstanceState = savedInstanceState
            }
        }
        //AdMob - ca-app-pub-3940256099942544~3347511713 is a sample ID for testing. In production use the actual app ID
        MobileAds.initialize(this){}
        //AdMob "ca-app-pub-3940256099942544/1033173712" is a test ad unit ID. In production use the add ad unit ID.
        if(mInterstitialAd == null){
            loadAd()
        }
        mValueEventListener1 = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                mFirebaseSourcesInfo = snapshot.getValue<SourcesInfo>()
                if (mFirebaseSourcesInfo == null) {
                    val publishersSelected = arrayListOf<String>()
                    val categoriesSelected = arrayListOf<String>()
                    val countriesSelected = arrayListOf<String>()
                    mFirebaseSourcesInfo = SourcesInfo(publishersSelected,categoriesSelected,countriesSelected)
                }
                Log.d(TAG, "valuelistener1")
                updateNoOfItemsSelected()
                updateUI(savedInstanceState, mFirebaseSourcesInfo)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        mValueEventListener2 = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                mUserSettings = snapshot.getValue<UserSettings>()
                val editor: Editor = pref.edit()
                if (mUserSettings != null) {
                    Log.d(TAG, "valuelistener2")
                    val fontSize: String? = mUserSettings?.fontSize
                    val theme: String? = mUserSettings?.theme
                    val topHeadlinesCountry: String? = mUserSettings?.topHeadLinesCountry
                    editor.putString(getString(R.string.pref_fontSize_key), fontSize)
                    editor.putString(getString(R.string.theme_key), theme)
                    editor.putString(getString(R.string.widgetKey_top_headlines),topHeadlinesCountry)
                    editor.apply()
                }
                else {
                    val locale: String = applicationContext.resources.configuration.locale.country
                    val defaultCountry = locale.lowercase()
                    val newUserSettings = UserSettings(getString(R.string.menu_medium_label),getString(R.string.lightLabel),defaultCountry)
                    editor.putString(getString(R.string.pref_fontSize_key),newUserSettings.fontSize)
                    editor.putString(getString(R.string.theme_key),newUserSettings.theme)
                    editor.putString(getString(R.string.widgetKey_top_headlines),newUserSettings.topHeadLinesCountry)
                    editor.apply()
                    mFirebaseAuth.currentUser?.uid?.let { settingsDB.child(it).child(getString(R.string.settings_label)).setValue(newUserSettings) }
                    //settingsDB.setValue(newUserSettings)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        mAuthStateListener = AuthStateListener {
            val user : FirebaseUser? = it.currentUser
            if (user!=null){
                Log.d(TAG, "AuthListener")
                sourceDB.child(user.uid).child(getString(R.string.sources_label)).addValueEventListener(mValueEventListener1)
                settingsDB.child(user.uid).child(getString(R.string.settings_label)).addValueEventListener(mValueEventListener2)
            }
            else{
                val googleIdp:AuthUI.IdpConfig = AuthUI.IdpConfig.GoogleBuilder().build()
                val facebookIdp:AuthUI.IdpConfig = AuthUI.IdpConfig.FacebookBuilder().build()
                val emailIdp:AuthUI.IdpConfig = AuthUI.IdpConfig.EmailBuilder().build()
                startActivityForResult(AuthUI.getInstance().createSignInIntentBuilder().setTheme(R.style.FirebaseLoginTheme).setIsSmartLockEnabled(false).setAvailableProviders(
                    listOf(googleIdp,facebookIdp,emailIdp)
                ).setLogo(R.drawable.communication).setTosAndPrivacyPolicyUrls(applicationContext.resources.getString(R.string.terms_of_service),applicationContext.resources.getString(R.string.privacy_policy)).build(),RC_SIGN_IN)
            }
        }
        clear_all_label.setOnClickListener { clearAllLabels() }
        done_label.setOnClickListener { doneLabel() }
    }

    private fun loadAd() {
        InterstitialAd.load(this,getString(R.string.AdUnit_ID),AdRequest.Builder().build(),object : InterstitialAdLoadCallback(){
            override fun onAdFailedToLoad(adError: LoadAdError) {
                mInterstitialAd = null
            }
            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                Log.d(TAG, "Ad was loaded.")
                mInterstitialAd = interstitialAd
                mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback(){
                    override fun onAdDismissedFullScreenContent() {
                        mInterstitialAd = null
                        mFirebaseAuth.currentUser?.uid?.let {
                            sourceDB.child(it).child(getString(R.string.sources_label)).setValue(mFirebaseSourcesInfo)
                        }
                        val newsArticlesActivity = Intent(applicationContext, NewsArticles::class.java)
                        newsArticlesActivity.putExtra(SOURCES, mFirebaseSourcesInfo)
                        startActivity(newsArticlesActivity)
                    }
                    override fun onAdShowedFullScreenContent() {

                    }
                }
            }
        })
    }

    private fun updateUI(savedInstanceState: Bundle?,mFirebaseSourcesInfo: SourcesInfo?){
        mSavedInstanceState = savedInstanceState
        setupViewPager(savedInstanceState,sources_container, mFirebaseSourcesInfo)
        tabs.setupWithViewPager(sources_container)
        Log.d(TAG, "update ui")
    }

    private fun setupViewPager(savedInstanceState: Bundle?,viewPager: ViewPager,mFirebaseSourcesInfo: SourcesInfo?){
        val adapter = SectionsPageAdapter(supportFragmentManager)
        val bundle : Bundle
        if(savedInstanceState == null) {
            bundle = Bundle()
            bundle.putParcelable(SOURCES, mFirebaseSourcesInfo)
        } else {
            bundle = savedInstanceState
        }
        val mPublishersFragment = PublishersSlidePageFragment()
        val mCategoriesFragment = CategoriesSlidePageFragment()
        val mCountriesFragment = CountriesSlidePageFragment()
        mPublishersFragment.arguments = bundle
        mCategoriesFragment.arguments = bundle
        mCountriesFragment.arguments = bundle
        adapter.addFragment(mPublishersFragment, resources.getString(R.string.publishers_label))
        adapter.addFragment(mCountriesFragment, resources.getString(R.string.countries_label))
        adapter.addFragment(mCategoriesFragment, resources.getString(R.string.categories_label))
        viewPager.adapter = adapter
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == RC_SIGN_IN){
            if(resultCode == RESULT_OK){
                val message = "Welcome " + mFirebaseAuth.currentUser?.displayName + "!"
                showSnackbar(message)
            }else if (resultCode == RESULT_CANCELED){
                Toast.makeText(this, "Sign in cancelled!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_activity_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.sign_out_menu) {
            //mFirebaseAuth.signOut()
                AuthUI.getInstance().signOut(this)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPause() {
        super.onPause()
        mFirebaseAuth.currentUser?.uid?.let {
            sourceDB.child(it).child(getString(R.string.sources_label)).removeEventListener(mValueEventListener1)
            settingsDB.child(it).child(getString(R.string.settings_label)).removeEventListener(mValueEventListener2)
        }
        mFirebaseAuth.removeAuthStateListener(mAuthStateListener)
        pref.unregisterOnSharedPreferenceChangeListener(this)
        Log.d(TAG, "released")
    }

    override fun onResume() {
        super.onResume()
        mFirebaseAuth.addAuthStateListener(mAuthStateListener)
        pref.registerOnSharedPreferenceChangeListener(this)
    }

    private fun showSnackbar(message : String){
        val view = findViewById<View>(R.id.mainLinearLayout)
        val duration = Snackbar.LENGTH_SHORT
        Snackbar.make(view, message, duration).show()
    }

    override fun onItemClickPublishers(item: String) {
        val mPublishersSelected = mFirebaseSourcesInfo?.mPublishersSelected

        if(mPublishersSelected != null){
            if(mPublishersSelected.contains(item)){
                mPublishersSelected.remove(item)
                val message = "You have removed $item"
                showSnackbar(message)
            } else {
                mPublishersSelected.add(item)
                showSnackbar("You have added $item")
            }
        }
        updateNoOfItemsSelected()
    }

    override fun onItemClickCategories(item: String) {
        val mCategoriesSelected = mFirebaseSourcesInfo?.mCategoriesSelected
        if(mCategoriesSelected != null){
            if(mCategoriesSelected.contains(item)){
                mCategoriesSelected.remove(item)
                val message = "You have removed $item"
                showSnackbar(message)
            } else {
                mCategoriesSelected.add(item)
                val message = "You have added $item"
                showSnackbar(message)
            }
        }
        updateNoOfItemsSelected()
    }

    override fun onItemClickCountries(item: String) {
        val mCountriesSelected = mFirebaseSourcesInfo?.mCountriesSelected
        if(mCountriesSelected != null){
            if(mCountriesSelected.contains(item)){
                mCountriesSelected.remove(item)
                val message = "You have removed $item"
                showSnackbar(message)
            } else {
                mCountriesSelected.add(item)
                val message = "You have added $item"
                showSnackbar(message)
            }
        }
        updateNoOfItemsSelected()
    }

    private fun updateNoOfItemsSelected(){
        if(mFirebaseSourcesInfo?.mPublishersSelected != null || mFirebaseSourcesInfo?.mCategoriesSelected != null || mFirebaseSourcesInfo?.mCountriesSelected != null) {
            val numberSelected = (mFirebaseSourcesInfo?.mPublishersSelected?.size ?: 0 )+ (mFirebaseSourcesInfo?.mCategoriesSelected?.size ?: 0) + (mFirebaseSourcesInfo?.mCountriesSelected?.size ?: 0)
            val selectedLabel = "$numberSelected selected"
            textView_no_items_selected.text = selectedLabel
        } else {
            textView_no_items_selected.text = getString(R.string.selected_label)
        }
    }

    private fun clearAllLabels(){
        mFirebaseAuth.currentUser?.uid?.let {sourceDB.child(it).child(getString(R.string.sources_label)).setValue(null)}
        mFirebaseSourcesInfo?.mPublishersSelected?.clear()
        mFirebaseSourcesInfo?.mCountriesSelected?.clear()
        mFirebaseSourcesInfo?.mCategoriesSelected?.clear()
        updateNoOfItemsSelected()
        updateUI(mSavedInstanceState, mFirebaseSourcesInfo)
    }

    private fun doneLabel(){
        if(mFirebaseSourcesInfo?.mCountriesSelected?.size==0 && mFirebaseSourcesInfo?.mCategoriesSelected?.size==0 && mFirebaseSourcesInfo?.mPublishersSelected?.size==0){
            val msg = "Please select news sources"
            showSnackbar(msg)
        }else if(mFirebaseSourcesInfo?.mPublishersSelected?.size==0){
            val msg = "Please select a publisher"
            showSnackbar(msg)
        }else if(mFirebaseSourcesInfo?.mCategoriesSelected?.size==0){
            val msg = "Please select a category"
            showSnackbar(msg)
        }else if(mFirebaseSourcesInfo?.mCountriesSelected?.size==0){
            val msg = "Please select a country"
            showSnackbar(msg)
        }else {
            if(mInterstitialAd!=null)
                mInterstitialAd?.show(this)
        }
    }

    override fun onSaveInstanceState(outState:Bundle) {
        Log.d(TAG,"onsave")
        super.onSaveInstanceState(outState)
        outState.putParcelable(SOURCES, mFirebaseSourcesInfo)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Log.d(TAG,"onshared")
        if(key.equals(resources.getString(R.string.theme_key))){
            recreate()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG,"shared release")
    }
}
