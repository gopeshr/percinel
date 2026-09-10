package gopesh.percinel.data

import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * One shared HTTP stack for TMDB and posters, with DNS-over-HTTPS as a safety net.
 *
 * Some mobile carriers (notably several Indian ISPs) DNS-block TMDB's hostnames, so search and
 * artwork silently fail on cellular while working on Wi-Fi. [SmartDns] resolves normally first —
 * no added latency on healthy networks — and falls back to DoH when the system resolver refuses
 * a host. If a request still fails at the socket/TLS layer (a resolver handing back a block-page
 * address), [DohRetry] pins that host to DoH and retries once.
 *
 * OkHttp already ships with the app via Coil, so this costs no extra download size.
 */
object Net {

    /** DoH endpoints addressed by IP literal, so resolving the resolver never needs DNS itself.
     *  Both Cloudflare and Google present certificates valid for these IPs. */
    private val DOH_URLS = listOf(
        "https://1.1.1.1/dns-query",
        "https://8.8.8.8/resolve",
    )

    private val TMDB_HOSTS = setOf("api.themoviedb.org", "image.tmdb.org")

    /** Hosts we've learned the local resolver can't be trusted for, this process. */
    private val forcedDoh = java.util.Collections.synchronizedSet(HashSet<String>())

    private val dohCache = java.util.Collections.synchronizedMap(HashMap<String, List<InetAddress>>())

    /** A minimal client used only to talk to the DoH endpoints (system DNS, IP literals). */
    private val bootstrap: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(SmartDns)
            .addInterceptor(DohRetry)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private object SmartDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            if (hostname !in forcedDoh) {
                try {
                    return Dns.SYSTEM.lookup(hostname)
                } catch (e: UnknownHostException) {
                    // Blocked or unresolvable locally — fall through to DoH.
                }
            }
            return resolveDoh(hostname)
                ?: throw UnknownHostException("Couldn't resolve $hostname (system DNS and DoH both failed)")
        }
    }

    /** Retries a failed TMDB request once with the host pinned to DoH, which covers resolvers
     *  that answer with a block-page address instead of refusing outright. */
    private object DohRetry : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val host = request.url.host
            return try {
                chain.proceed(request)
            } catch (e: IOException) {
                if (host in TMDB_HOSTS && forcedDoh.add(host)) {
                    dohCache.remove(host)
                    chain.proceed(request)
                } else {
                    throw e
                }
            }
        }
    }

    private fun resolveDoh(hostname: String): List<InetAddress>? {
        dohCache[hostname]?.let { if (it.isNotEmpty()) return it }
        for (base in DOH_URLS) {
            val ips = runCatching { queryDoh(base, hostname) }.getOrNull()
            if (!ips.isNullOrEmpty()) {
                dohCache[hostname] = ips
                return ips
            }
        }
        return null
    }

    /** RFC 8484 JSON ("application/dns-json"), which both endpoints speak. */
    private fun queryDoh(base: String, hostname: String): List<InetAddress> {
        val url = "$base?name=$hostname&type=A"
        val req = Request.Builder()
            .url(url)
            .header("accept", "application/dns-json")
            .build()
        bootstrap.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body?.string() ?: return emptyList()
            val answers = JSONObject(body).optJSONArray("Answer") ?: return emptyList()
            val out = ArrayList<InetAddress>()
            for (i in 0 until answers.length()) {
                val a = answers.getJSONObject(i)
                if (a.optInt("type") != 1) continue // A records only
                val ip = a.optString("data").trim()
                if (ip.isEmpty()) continue
                runCatching { InetAddress.getByName(ip) }.getOrNull()?.let {
                    // getByName on a literal never hits DNS; re-wrap so the address carries the hostname.
                    out.add(InetAddress.getByAddress(hostname, it.address))
                }
            }
            return out
        }
    }
}
