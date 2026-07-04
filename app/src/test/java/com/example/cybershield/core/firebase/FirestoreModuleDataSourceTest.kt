package com.example.cybershield.core.firebase

import com.example.cybershield.core.firebase.model.ModuleDto
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FirestoreModuleDataSourceTest {
    private lateinit var firestore: FirebaseFirestore
    private lateinit var dataSource: FirestoreModuleDataSource

    private lateinit var modulesCollection: CollectionReference
    private lateinit var orderedQuery: Query

    @Before
    fun setUp() {
        firestore = mockk()
        dataSource = FirestoreModuleDataSource(firestore)

        modulesCollection = mockk()
        orderedQuery = mockk()

        every { firestore.collection("modules") } returns modulesCollection
        every { modulesCollection.orderBy("order") } returns orderedQuery
    }

    private fun <T> completedTask(value: T): Task<T> {
        val task = mockk<Task<T>>()
        every { task.isComplete } returns true
        every { task.isCanceled } returns false
        every { task.exception } returns null
        every { task.result } returns value
        return task
    }

    @Test
    fun `getAllModules queries the modules collection ordered by order field`() =
        runTest {
            val dto1 = ModuleDto(id = "m1", title = "Intro", order = 0)
            val dto2 = ModuleDto(id = "m2", title = "Advanced", order = 1)
            val snapshot = mockk<QuerySnapshot>()
            every { snapshot.toObjects(ModuleDto::class.java) } returns listOf(dto1, dto2)
            every { orderedQuery.get() } returns completedTask(snapshot)

            val result = dataSource.getAllModules()

            assertEquals(listOf(dto1, dto2), result)
        }

    @Test
    fun `getAllModules returns empty list when no modules exist`() =
        runTest {
            val snapshot = mockk<QuerySnapshot>()
            every { snapshot.toObjects(ModuleDto::class.java) } returns emptyList()
            every { orderedQuery.get() } returns completedTask(snapshot)

            val result = dataSource.getAllModules()

            assertEquals(emptyList<ModuleDto>(), result)
        }

    @Test
    fun `getModuleById returns the mapped module when it exists`() =
        runTest {
            val moduleDoc = mockk<DocumentReference>()
            every { modulesCollection.document("m1") } returns moduleDoc
            val docSnapshot = mockk<DocumentSnapshot>()
            val dto = ModuleDto(id = "m1", title = "Intro")
            every { docSnapshot.toObject(ModuleDto::class.java) } returns dto
            every { moduleDoc.get() } returns completedTask(docSnapshot)

            val result = dataSource.getModuleById("m1")

            assertEquals(dto, result)
        }

    @Test
    fun `getModuleById returns null when the document does not exist`() =
        runTest {
            val moduleDoc = mockk<DocumentReference>()
            every { modulesCollection.document("missing") } returns moduleDoc
            val docSnapshot = mockk<DocumentSnapshot>()
            every { docSnapshot.toObject(ModuleDto::class.java) } returns null
            every { moduleDoc.get() } returns completedTask(docSnapshot)

            val result = dataSource.getModuleById("missing")

            assertNull(result)
        }
}
