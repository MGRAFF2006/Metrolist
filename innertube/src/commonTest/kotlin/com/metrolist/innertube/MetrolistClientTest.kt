package com.metrolist.innertube

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

@OptIn(ExperimentalSerializationApi::class)
class MetrolistClientTest {
    @Test
    fun sessionIsUsedForLibraryAndClearedOnSignOut() = runTest {
        val authorizations = mutableListOf<String?>()
        val bodies = mutableListOf<String>()
        val client = MetrolistClient(httpClient(MockEngine {
            authorizations += it.headers[HttpHeaders.Authorization]
            bodies += (it.body as io.ktor.http.content.TextContent).text
            respond("""{"responseContext": {}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }))
        try {
            client.setSession("SAPISID=fake-test-session")
            client.library().getOrThrow()
            client.library(continuation = "next-page").getOrThrow()
            client.setSession(null)
            client.library().getOrThrow()
            assertNotNull(authorizations[0])
            assertNotNull(authorizations[1])
            assertNull(authorizations[2])
            assertEquals("next-page", Json.parseToJsonElement(bodies[1]).let {
                (it as kotlinx.serialization.json.JsonObject)["continuation"]?.let { value ->
                    (value as kotlinx.serialization.json.JsonPrimitive).content
                }
            })
        } finally { client.close() }
    }

    @Test
    fun cancelledRequestIsNotConvertedIntoAnErrorResult() = runTest {
        val client = MetrolistClient(httpClient(MockEngine { throw CancellationException("cancelled") }))
        try {
            assertFailsWith<CancellationException> { client.home() }
        } finally { client.close() }
    }

    private fun httpClient(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false }) }
    }
}
