package com.example.cybershield.core.firebase

import app.cash.turbine.test
import com.example.cybershield.core.domain.util.Result
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FirestoreLeaderboardDataSourceTest {
    private lateinit var firestore: FirebaseFirestore
    private lateinit var dataSource: FirestoreLeaderboardDataSource

    private lateinit var collection: CollectionReference
    private lateinit var query: Query
    private lateinit var registration: ListenerRegistration

    @Before
    fun setUp() {
        firestore = mockk()
        dataSource = FirestoreLeaderboardDataSource(firestore)

        collection = mockk()
        query = mockk()
        registration = mockk(relaxed = true)

        every { firestore.collection("leaderboard") } returns collection
        every { collection.orderBy("xp", Query.Direction.DESCENDING) } returns query
        every { query.limit(any()) } returns query
    }

    private fun fakeDoc(
        id: String,
        data: Map<String, Any?>,
    ): DocumentSnapshot {
        val doc = mockk<DocumentSnapshot>()
        every { doc.id } returns id
        every { doc.data } returns data
        return doc
    }

    @Test
    fun `topUsers emits mapped list with docId injected on success`() =
        runTest {
            val capturedListener = slot<EventListener<QuerySnapshot>>()
            every { query.addSnapshotListener(capture(capturedListener)) } returns registration

            val snapshot = mockk<QuerySnapshot>()
            val doc1 = fakeDoc("user1", mapOf("displayName" to "Alice", "xp" to 100L))
            val doc2 = fakeDoc("user2", mapOf("displayName" to "Bob", "xp" to 80L))
            every { snapshot.documents } returns listOf(doc1, doc2)

            dataSource.topUsers(10).test {
                capturedListener.captured.onEvent(snapshot, null)

                val emitted = awaitItem()
                assertTrue(emitted is Result.Success)
                val list = (emitted as Result.Success).data
                assertEquals(2, list.size)
                assertEquals("user1", list[0]["_docId"])
                assertEquals("Alice", list[0]["displayName"])
                assertEquals("user2", list[1]["_docId"])

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `topUsers emits empty list when snapshot has no documents`() =
        runTest {
            val capturedListener = slot<EventListener<QuerySnapshot>>()
            every { query.addSnapshotListener(capture(capturedListener)) } returns registration
            val snapshot = mockk<QuerySnapshot>()
            every { snapshot.documents } returns emptyList()

            dataSource.topUsers(5).test {
                capturedListener.captured.onEvent(snapshot, null)

                val emitted = awaitItem()
                assertTrue(emitted is Result.Success)
                assertTrue((emitted as Result.Success).data.isEmpty())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `topUsers propagates Firestore errors instead of emitting an empty list`() =
        runTest {
            val capturedListener = slot<EventListener<QuerySnapshot>>()
            every { query.addSnapshotListener(capture(capturedListener)) } returns registration
            val error = mockk<FirebaseFirestoreException>(relaxed = true)

            dataSource.topUsers(10).test {
                capturedListener.captured.onEvent(null, error)

                val emitted = awaitItem()
                assertTrue(emitted is Result.Error)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `topUsers removes the Firestore listener when the flow is cancelled`() =
        runTest {
            val capturedListener = slot<EventListener<QuerySnapshot>>()
            every { query.addSnapshotListener(capture(capturedListener)) } returns registration

            dataSource.topUsers(10).test {
                cancelAndIgnoreRemainingEvents()
            }

            verify(exactly = 1) { registration.remove() }
        }

    @Test
    fun `topUsers applies the requested limit to the query`() =
        runTest {
            val capturedListener = slot<EventListener<QuerySnapshot>>()
            every { query.addSnapshotListener(capture(capturedListener)) } returns registration
            val snapshot = mockk<QuerySnapshot>()
            every { snapshot.documents } returns emptyList()

            dataSource.topUsers(25).test {
                capturedListener.captured.onEvent(snapshot, null)
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            verify(exactly = 1) { query.limit(25L) }
        }
}
