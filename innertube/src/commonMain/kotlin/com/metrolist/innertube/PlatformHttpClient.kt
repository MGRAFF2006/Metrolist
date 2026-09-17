package com.metrolist.innertube

import io.ktor.client.HttpClient

internal expect fun createPlatformHttpClient(): HttpClient
