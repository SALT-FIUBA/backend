package io.kauth.service.accessrequest

import io.kauth.abstractions.command.throwOnFailureHandler
import io.kauth.abstractions.result.Ok
import io.kauth.client.eventStore.EventStoreClient
import io.kauth.exception.ApiException
import io.kauth.monad.apicall.ApiCall
import io.kauth.monad.apicall.ApiCallContext
import io.kauth.monad.apicall.toApiCall
import io.kauth.monad.stack.AppContext
import io.kauth.monad.stack.AppStack
import io.kauth.service.accessrequest.AccessRequestApi.Command
import io.kauth.service.accessrequest.AccessRequestService
import io.kauth.service.occasion.OccasionService
import io.kauth.service.auth.jwt.Jwt
import io.kauth.service.config.AppConfig
import io.kauth.service.occasion.Occasion
import io.kauth.service.reservation.Reservation
import io.kauth.service.reservation.ReservationService
import io.kauth.util.AppLogger
import io.kauth.util.Async
import io.kauth.util.MutableClassMap
import io.kauth.util.not
import io.ktor.server.application.*
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database

class AccessRequestApiTest {

    // Helper to initialize mocks and context
    private fun initMocks(
        occasionService: OccasionService.Interface = mockk(),
        accessRequestService: AccessRequestService.Interface = mockk(),
        reservationService: ReservationService.Interface = mockk(),
        logger: AppLogger = mockk(),
        eventStore: EventStoreClient = mockk(),
        appConfig: AppConfig = mockk(),
        db: Database = mockk(),
        jwt: Jwt
    ): ApiCallContext {
        val services = !MutableClassMap.new
        !services.set(OccasionService.Interface::class, occasionService)
        !services.set(AppLogger::class, logger)
        !services.set(AccessRequestService.Interface::class, accessRequestService)
        !services.set(ReservationService.Interface::class, reservationService)
        !services.set(EventStoreClient::class, eventStore)
        val appContext = AppContext(
            ktor = mockk<Application>(),
            serialization = Json {  },
            services = services,
            db = db,
            appConfig = appConfig
        )
        return  ApiCallContext(appContext, jwt)
    }

    @Test
    fun `should create access request for valid input`() = runBlocking {
        val occasionId = UUID.randomUUID()
        val userId = "user@example.com"
        val categoryName = "VIP"
        val description = "Requesting access"
        val places = 2
        val fanPageId = UUID.randomUUID()
        val occasionState = Occasion.State(
            status = Occasion.Status.open,
            fanPageId = fanPageId,
            name = "Test Occasion",
            description = "desc",
            categories = emptyList(),
            startDateTime = Clock.System.now(),
            endDateTime = Clock.System.now(),
            createdAt = Clock.System.now(),
            resource = "resource",
            disabled = false,
            location = ""
        )
        val occasionService = mockk<OccasionService.Interface>()
        val accessRequestService = mockk<AccessRequestService.Interface>()
        val reservationService = mockk<ReservationService.Interface>()
        val logger = mockk<AppLogger>()
        val eventStore = mockk<EventStoreClient>()
        val jwt = Jwt(
            payload = Jwt.Payload(
                email = userId,
                id = "user-id",
                roles = listOf("user")
            )
        )
        val  apiCallContext = initMocks(
            occasionService = occasionService,
            accessRequestService = accessRequestService,
            reservationService = reservationService,
            logger = logger,
            eventStore = eventStore,
            jwt = jwt
        )
        every { occasionService.query.readState(occasionId) } returns Async { occasionState }
        every { reservationService.query.readState("request-${occasionId}-${userId}") } returns Async { null }
        every { reservationService.command.handle(any())(any()) } returns AppStack.Do { Ok }
        every { accessRequestService.command.handle(any())(any()) } returns AppStack.Do { Ok }
        every { logger.info(any()) } returns Unit
        !Command.create(
            occasionId = occasionId,
            userId = userId,
            categoryName = categoryName,
            description = description,
            places = places
        ).run(apiCallContext)
        verify {accessRequestService.command.handle(any())(withArg { cmd ->
            assert(cmd is AccessRequest.Command.CreateRequest)
            cmd as AccessRequest.Command.CreateRequest
            assertEquals(occasionId, cmd.occasionId)
            assertEquals(userId, cmd.userId)
            assertEquals(categoryName, cmd.categoryName)
            assertEquals(description, cmd.description)
            assertEquals(places, cmd.places)
        }) }
    }

    @Test
    fun `should fail to create access request if occasion does not exist`() = runBlocking {
        val occasionId = UUID.randomUUID()
        val userId = "user@example.com"
        val categoryName = "VIP"
        val description = "Requesting access"
        val places = 2
        val occasionService = mockk<OccasionService.Interface>()
        val jwt = Jwt(
            payload = Jwt.Payload(
                email = userId,
                id = "user-id",
                roles = listOf("user")
            )
        )
        val  apiCallContext = initMocks(
            occasionService = occasionService,
            jwt = jwt
        )
        every { occasionService.query.readState(occasionId) } returns Async { null }
        val ex = assertThrows(ApiException::class.java) {
            runBlocking {
                !Command.create(
                    occasionId = occasionId,
                    userId = userId,
                    categoryName = categoryName,
                    description = description,
                    places = places
                ).run(apiCallContext)
            }
        }
        assertTrue(ex.message!!.contains("Occasion not found"))
    }

    @Test
    fun `should fail to create access request if category is invalid`() = runBlocking {
        val occasionId = UUID.randomUUID()
        val userId = "user@example.com"
        val categoryName = "INVALID"
        val description = "Requesting access"
        val places = 2
        val fanPageId = UUID.randomUUID()
        val occasionState = Occasion.State(
            status = Occasion.Status.open,
            fanPageId = fanPageId,
            name = "Test Occasion",
            description = "desc",
            categories = listOf(Occasion.CategoryState("VIP", 10, emptyList(), emptyList())),
            startDateTime = Clock.System.now(),
            endDateTime = Clock.System.now(),
            createdAt = Clock.System.now(),
            resource = "resource",
            disabled = false,
            location = ""
        )
        val occasionService = mockk<OccasionService.Interface>()
        val jwt = Jwt(
            payload = Jwt.Payload(
                email = userId,
                id = "user-id",
                roles = listOf("user")
            )
        )
        val  apiCallContext = initMocks(
            occasionService = occasionService,
            jwt = jwt
        )
        every { occasionService.query.readState(occasionId) } returns Async { occasionState }
        val ex = assertThrows(ApiException::class.java) {
            runBlocking {
                !Command.create(
                    occasionId = occasionId,
                    userId = userId,
                    categoryName = categoryName,
                    description = description,
                    places = places
                ).run(apiCallContext)
            }
        }
        assertTrue(ex.message!!.contains("Category not found"))
    }

}
