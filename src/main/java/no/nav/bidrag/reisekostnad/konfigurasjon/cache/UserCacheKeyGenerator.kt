package no.nav.bidrag.reisekostnad.konfigurasjon.cache
import no.nav.bidrag.reisekostnad.konfigurasjon.Tokeninfo
import no.nav.bidrag.reisekostnad.model.SYSTEMBRUKER_ID
import org.springframework.cache.interceptor.SimpleKeyGenerator
import java.lang.reflect.Method

class UserCacheKeyGenerator : SimpleKeyGenerator() {
    override fun generate(target: Any, method: Method, vararg params: Any): Any {
        return toUserCacheKey(super.generate(target, method, *params))
    }

    private fun toUserCacheKey(key: Any): UserCacheKey {
        val userId = if (Tokeninfo.erSystembruker()) SYSTEMBRUKER_ID else Tokeninfo.hentPaaloggetPerson()
        return UserCacheKey(userId ?: "UKJENT", key)
    }
}
