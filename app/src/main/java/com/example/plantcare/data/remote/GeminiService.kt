package com.example.plantcare.data.remote

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface GeminiService {
    @POST("models/gemini-2.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body req: GenerateContentRequest
    ): GenerateContentResponse
}

data class GenerateContentRequest(
    val contents: List<Content>
)

data class Content(val parts: List<Part>)

data class Part(
    val text: String? = null,
    val inline_data: InlineData? = null
)

data class InlineData(val mime_type: String, val data: String)

data class GenerateContentResponse(val candidates: List<Candidate>?)

data class Candidate(val content: Content?)


