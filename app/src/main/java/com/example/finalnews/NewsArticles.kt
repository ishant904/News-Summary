package com.example.finalnews

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.*
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.loader.app.LoaderManager
import androidx.loader.content.AsyncTaskLoader
import androidx.loader.content.Loader
import androidx.preference.PreferenceManager
import androidx.viewpager.widget.ViewPager
import com.example.finalnews.Adapters.SectionsPageAdapter
import com.example.finalnews.Fragments.NewsSourceFragment
import com.example.finalnews.Fragments.TabletFragment
import com.example.finalnews.Sources.ArticleItem
import com.example.finalnews.Sources.SourcesInfo
import com.example.finalnews.Utilities.GetOkHttpResponse
import com.example.finalnews.Utilities.TranslateSources
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.android.synthetic.main.activity_news_articles.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

class NewsArticles : AppCompatActivity(),LoaderManager.LoaderCallbacks<ArrayList<ArrayList<String>>>,NewsSourceFragment.FragmentListener,SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var mSourcesInfo : SourcesInfo
    companion object{
        const val newsApiStartPoint = "https://newsapi.org/v2/top-headlines?"
        private val NEWS_API_KEY: String = BuildConfig.NEWS_API_KEY
        private const val publisherArticlesKey = "PublisherArticles"
        private const val countriesArticlesKey = "countriesArticles"
        const val tabletUriKey = "uriKey"
        const val bookmarksKey = "bookmarkedArticlesKey"
        const val fontSizeKey = "fontSizeKey"
        private val TAG = NewsArticles::class.java.simpleName
        const val clickedArticleURLTAG = "Clicked on Article to open URL"
        const val clickedArticleBookmarkTAG = "Clicked on Article Bookmark"
        const val clickedSwipeToRefreshTAG = "Clicked SwipeToRefresh"
        const val publishersJSONStringKey = "publishersKey"
        const val countriesAndCategoriesJSONStringKey = "countriesCategoriesKey"
    }
    private lateinit var mNewsJsonList: ArrayList<ArrayList<String>>
    private lateinit var callback: LoaderManager.LoaderCallbacks<ArrayList<ArrayList<String>>>
    private lateinit var mFirebaseAuth: FirebaseAuth
    private lateinit var databaseRef: DatabaseReference
    private var connected = false
    private lateinit var pref: SharedPreferences
    private lateinit var broadcastReceiver: BroadcastReceiver
    private lateinit var client: OkHttpClient
    private lateinit var bookmarkedArticles:ArrayList<ArticleItem>
    private var twoPane:Boolean = false
    private lateinit var tabletFragment: TabletFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        pref = PreferenceManager.getDefaultSharedPreferences(this)
        val themeName = pref.getString(getString(R.string.theme_key),getString(R.string.theme_default))
         if (themeName == getString(R.string.lightLabel)) {
            setTheme(R.style.LightTheme)
        } else if (themeName == getString(R.string.darkLabel)) {
            setTheme(R.style.DarkTheme)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_news_articles)
        setSupportActionBar(news_articles_toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        twoPane = (findViewById<ViewGroup>(R.id.fragment_container_uri) !=null)
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        connected = isConnectedToInternet(connectivityManager)
        mFirebaseAuth = Firebase.auth
        databaseRef = Firebase.database.getReference(getString(R.string.users_label))
        bookmarkedArticles = arrayListOf()
        mNewsJsonList = arrayListOf()
        getListOfBookmarkedArticles()
        broadcastReceiver = object : BroadcastReceiver(){
            override fun onReceive(context: Context?, intent: Intent?) {
                    finish()
            }
        }
        val intentFilter = IntentFilter()
        intentFilter.addAction(getString(R.string.logout_action))
        registerReceiver(broadcastReceiver, intentFilter)
        val intent = intent
        mSourcesInfo = intent.getParcelableExtra(MainActivity.SOURCES)!!
        client =  OkHttpClient()
        callback = this@NewsArticles
        if(savedInstanceState == null) {
            getSupportLoaderManager().initLoader(0, null, callback) //This is for loading the news publishers json string
        } else {
            mNewsJsonList.add(savedInstanceState.getStringArrayList(publisherArticlesKey)!!)
            mNewsJsonList.add(savedInstanceState.getStringArrayList(countriesArticlesKey)!!)
            setUpViewPager(news_articles_viewPager)
            sources_tabs.setupWithViewPager(news_articles_viewPager)
        }
        pref.registerOnSharedPreferenceChangeListener(this)
    }

    private fun isConnectedToInternet(connectivityManager : ConnectivityManager): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nw = connectivityManager.activeNetwork ?: return false
            val actNw = connectivityManager.getNetworkCapabilities(nw) ?: return false
            return when {
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                //for other device how are able to connect with Ethernet
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                //for check internet over Bluetooth
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> true
                else -> false
            }
        } else{
            val networks = connectivityManager.allNetworks
            for(n:Network in networks){
                connectivityManager.getNetworkInfo(n)?.let {
                    if(it.isConnected)
                        return true
                }
            }
        }
        return false
    }

    private fun getListOfBookmarkedArticles() {
        Log.d(TAG,"oncreate")
        mFirebaseAuth.currentUser?.uid?.let { databaseRef.child(it).child(getString(R.string.bookmarks_label)).addValueEventListener(object : ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {
                bookmarkedArticles.clear()
                for(i in snapshot.children){
                    bookmarkedArticles.add(i.getValue(ArticleItem::class.java)!!)
                }

            }

            override fun onCancelled(error: DatabaseError) {
                TODO("Not yet implemented")
            }
        })
        }
    }

    private fun setUpViewPager(mNewsViewPager: ViewPager){
        val adapter = SectionsPageAdapter(supportFragmentManager)
        for(i in 0 until mNewsJsonList[0].size){
            val publisher = mSourcesInfo.mPublishersSelected!![i]
            val publisherBundle = Bundle()
            publisherBundle.putString(publishersJSONStringKey, mNewsJsonList[0][i])
            val fontSize = pref.getString(getString(R.string.pref_fontSize_key),getString(R.string.fontSize_default))
            publisherBundle.putString(fontSizeKey, fontSize)
            val mNewsSourceFragment = NewsSourceFragment()
            mNewsSourceFragment.arguments = publisherBundle
            adapter.addFragment(mNewsSourceFragment, publisher)
        }
        var b=0
        while (b<mNewsJsonList[1].size){
            for(y in 0 until mSourcesInfo.mCountriesSelected!!.size){
                val country = mSourcesInfo.mCountriesSelected!![y]
                for(a in 0 until mSourcesInfo.mCategoriesSelected!!.size){
                    val category = mSourcesInfo.mCategoriesSelected!![a]
                    val countryCategoryBundle = Bundle()
                    val fontSize = pref.getString(getString(R.string.pref_fontSize_key),getString(R.string.fontSize_default))
                    countryCategoryBundle.putString(fontSizeKey, fontSize)
                    try{
                        countryCategoryBundle.putString(countriesAndCategoriesJSONStringKey,mNewsJsonList[1][b])
                    }catch (e:IndexOutOfBoundsException){
                        e.printStackTrace()
                    }
                    val mCountryCategorySourceFragment = NewsSourceFragment()
                    mCountryCategorySourceFragment.arguments = countryCategoryBundle
                    val countryAndCategoryLabelTab = "$country ($category)"
                    adapter.addFragment(mCountryCategorySourceFragment, countryAndCategoryLabelTab)
                    b++
                }
            }
        }
        progress_bar_articles.visibility = View.GONE
        mNewsViewPager.adapter = adapter
    }

    @SuppressLint("StaticFieldLeak")
    override fun onCreateLoader(id: Int, args: Bundle?): Loader<ArrayList<ArrayList<String>>> {
        return object : AsyncTaskLoader<ArrayList<ArrayList<String>>>(this) {
            override fun onStartLoading() {
                if(mNewsJsonList.size==0)
                    forceLoad()
                else
                    deliverResult(mNewsJsonList)
            }

            override fun loadInBackground(): ArrayList<ArrayList<String>> {
                Log.d(TAG,"loader loadinback")
                val mPublisherJSONList = arrayListOf<String>()
                val mCountryCategoryJSONList = arrayListOf<String>()
                try {
                    for (i in 0 until mSourcesInfo.mPublishersSelected!!.size) {
                        val newsPublisher: String = mSourcesInfo.mPublishersSelected!![i]
                        val newsPublisherForUrl: String =
                            TranslateSources.translateNewsPublisher(newsPublisher)
                        val publisherURL =
                            newsApiStartPoint + "sources=" + newsPublisherForUrl + "&apiKey=" + NEWS_API_KEY
                        val request = Request.Builder().url(publisherURL).build()
                        val getOkHttpResponse = GetOkHttpResponse(client, request)
                        val jsonPublisherDataResponse: String? = getOkHttpResponse.run()
                        if (jsonPublisherDataResponse != null) {
                            mPublisherJSONList.add(jsonPublisherDataResponse)
                        }
                    }
                    for (i in 0 until mSourcesInfo.mCountriesSelected!!.size) {
                        val country = mSourcesInfo.mCountriesSelected!![i]
                        val countryForUrl = TranslateSources.translateCountry(country)
                        for (x in 0 until mSourcesInfo.mCategoriesSelected!!.size) {
                            val category = mSourcesInfo.mCategoriesSelected!![x]
                            val categoryForUrl = TranslateSources.translateCategory(category)
                            val countryAndCategoryURL =
                                newsApiStartPoint + "country=" + countryForUrl + "&category=" + categoryForUrl + "&apiKey=" + NEWS_API_KEY
                            val request = Request.Builder().url(countryAndCategoryURL).build()
                            val getOkHttpResponse = GetOkHttpResponse(client, request)
                            val jsonCountryAndCategoryDataResponse = getOkHttpResponse.run()
                            if (jsonCountryAndCategoryDataResponse != null) {
                                mCountryCategoryJSONList.add(jsonCountryAndCategoryDataResponse)
                            }
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                mNewsJsonList.add(mPublisherJSONList)
                mNewsJsonList.add(mCountryCategoryJSONList)
                return mNewsJsonList
            }

            override fun deliverResult(data: ArrayList<ArrayList<String>>?) {
                if (data != null) {
                    mNewsJsonList = data
                }
                super.deliverResult(data)
            }
        }
    }

    override fun onLoadFinished(loader: Loader<ArrayList<ArrayList<String>>>,data: ArrayList<ArrayList<String>>?) {
        if (data == null || !connected) {
            showErrorMessage()
        } else {
            Log.d(TAG, "loader was finished successfully.")
            checkArticlesForErrors(mNewsJsonList[0])
            checkArticlesForErrors(mNewsJsonList[1])
            setUpViewPager(news_articles_viewPager)
            sources_tabs.setupWithViewPager(news_articles_viewPager)
        }
    }

    private fun checkArticlesForErrors(mJSONStringList: ArrayList<String>) {
        for (i in 0 until mJSONStringList.size){
            val mJsonString = mJSONStringList[i]
            val newsArticlesJSONresponse = JSONObject(mJsonString)
            val status = newsArticlesJSONresponse.getString(getString(R.string.status_key))
            if (status == getString(R.string.error_label)) {
                val errorCode = newsArticlesJSONresponse.getString(getString(R.string.code_key))
                val errorMessage = newsArticlesJSONresponse.getString(getString(R.string.message_key))
                val errorTitle = "Error: $errorCode"
                val builder = AlertDialog.Builder(this)
                builder.setMessage(errorMessage)
                    .setTitle(errorTitle)
                    .setPositiveButton(R.string.ok_confirm) { dialogInterface, _ -> dialogInterface.dismiss() }
                builder.show()
                break
            }
        }
    }

    private fun showErrorMessage() {
        progress_bar_articles.visibility = View.GONE
        error_msg.visibility = View.VISIBLE
    }

    override fun onLoaderReset(loader: Loader<ArrayList<ArrayList<String>>>) {
        TODO("Not yet implemented")
    }

    override fun onFragmentClick(TAG: String, articleItem: ArticleItem) {
        when(TAG){
            clickedSwipeToRefreshTAG ->
                updateNewsData()
            clickedArticleURLTAG -> {
                if(!twoPane){
                    val webpage = Uri.parse(articleItem.url)
                    val intent = Intent (Intent.ACTION_VIEW, webpage)
                    if (intent.resolveActivity(applicationContext.packageManager) != null) {
                        startActivity(intent)
                    }
                }else{
                    val bundle =Bundle()
                    bundle.putString(tabletUriKey,articleItem.url)
                    tabletFragment = TabletFragment()
                    tabletFragment.arguments = bundle
                    supportFragmentManager.beginTransaction().replace(R.id.fragment_container_uri,tabletFragment).commit()
                }
            }
            clickedArticleBookmarkTAG -> {
                if(bookmarkedArticles.contains(articleItem)){
                    bookmarkedArticles.remove(articleItem)
                    val message = "You have removed " + "\"" + articleItem.title + "\""
                    showSnackBar(message)
                }else{
                    bookmarkedArticles.add(articleItem)
                    val message = "You have added " + "\"" + articleItem.title + "\""
                    showSnackBar(message)
                }
                mFirebaseAuth.currentUser?.uid?.let { databaseRef.child(it).child(getString(R.string.bookmarks_label)).setValue(bookmarkedArticles)}
                //news_articles_viewPager.adapter?.notifyDataSetChanged()
            }

        }
    }

    private fun showSnackBar(message:String){
        val view:View = findViewById(R.id.news_articles_coordinatorLayout)
        val duration = Snackbar.LENGTH_SHORT
        Snackbar.make(view, message, duration).show()
    }

    private fun updateNewsData(){
        mNewsJsonList.clear()
        getSupportLoaderManager().restartLoader(0, null, callback)
        //news_articles_viewPager.adapter?.notifyDataSetChanged()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Log.d(TAG,"onshared")
        if(key.equals(resources.getString(R.string.theme_key))){
            recreate()
        } else if (key.equals(resources.getString(R.string.pref_fontSize_key))){
            /*if(resources.getBoolean(R.bool.isTablet)){
                recreate()
            }*/
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId){
            android.R.id.home ->onBackPressed()
            R.id.menu_refresh -> updateNewsData()
            R.id.menu_settings -> { val intent = Intent(applicationContext, SettingsActivity::class.java)
            startActivity(intent) }
            R.id.menu_bookmarks -> { val bookintent = Intent(applicationContext,BookmarksActivity::class.java)
                bookintent.putParcelableArrayListExtra(bookmarksKey, bookmarkedArticles)
                startActivity(bookintent) }
            R.id.sign_out_menu -> {
                mFirebaseAuth.signOut()
                val broadIntent = Intent()
                broadIntent.action = getString(R.string.logout_action)
                sendBroadcast(broadIntent)
            }

        }

        return super.onOptionsItemSelected(item)
    }

    override fun onSaveInstanceState(outState:Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(publisherArticlesKey, mNewsJsonList[0])
        outState.putStringArrayList(countriesArticlesKey, mNewsJsonList[1])
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "destroy new article")
        pref.unregisterOnSharedPreferenceChangeListener(this)
        unregisterReceiver(broadcastReceiver)
    }
}
