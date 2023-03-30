package no.nav.bidrag.reisekostnad.konfigurasjon.cache

import org.springframework.cache.annotation.Cacheable
import org.springframework.core.annotation.AliasFor

/**
 * Annotation indicating that the result of invoking a method can be cached restricted to the current user.
 *
 * @see Cacheable
 *
 * @see UserCacheKey
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
@MustBeDocumented
@Cacheable(keyGenerator = UserCacheKey.GENERATOR_BEAN)
annotation class UserCacheable(
    @get:AliasFor(annotation = Cacheable::class) vararg val value: String = []
)
