package com.riyaz.rssmoneymanager
import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object RssDynamicPricing {
    const val PROJECT_KEY="rss-money-manager"; const val CORE_URL="https://rsscore.cv"
    fun refresh(context:Context,onComplete:((Boolean)->Unit)?=null){thread{var ok=false;try{val c=URL("$CORE_URL/api/v1/pricing?project_key=$PROJECT_KEY").openConnection() as HttpURLConnection;c.connectTimeout=10000;c.readTimeout=15000;if(c.responseCode in 200..299){val p=JSONObject(c.inputStream.bufferedReader().use{it.readText()}).optJSONArray("plans");if(p!=null)context.getSharedPreferences("rss_pricing",0).edit().putString("json",p.toString()).putLong("updated_at",System.currentTimeMillis()).apply();ok=p!=null};c.disconnect()}catch(_:Exception){};onComplete?.let{context.mainExecutor.execute{it(ok)}}}}
    fun cached(context:Context)=context.getSharedPreferences("rss_pricing",0).getString("json",null)
    fun currentPlan(context: Context): JSONObject? = try { val plans = org.json.JSONArray(cached(context) ?: "[]"); if (plans.length() == 0) null else plans.getJSONObject(0) } catch (_: Exception) { null }
    fun finalPriceLkr(context: Context): Int? = currentPlan(context)?.optInt("final_price_lkr", -1)?.takeIf { it >= 0 }
    fun listPriceLkr(context: Context): Int? = currentPlan(context)?.optInt("list_price_lkr", -1)?.takeIf { it >= 0 }
    fun discountLkr(context: Context): Int? = currentPlan(context)?.optInt("discount_lkr", 0)
}
