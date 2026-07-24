package com.borinquenterrier.cef

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.borinquenterrier.cef.db.AppDatabase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SourceRepositoryTest : FunSpec({

    lateinit var driver: SqlDriver
    lateinit var database: AppDatabase
    lateinit var repository: SourceRepository

    beforeEach {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AppDatabase.Schema.create(driver)
        database = AppDatabase(driver)
        repository = SqlDelightSourceRepository(database)
    }

    afterEach {
        driver.close()
    }

    test("saveSource inserts source and fragments successfully") {
        val fragment = SourceFragment(
            text = "Line 1 of syllabus",
            pageNumber = 1,
            sectionTitle = "Introduction"
        )
        val sourceItem = SourceItem(
            title = "syllabus.pdf",
            fragments = listOf(fragment),
            category = SourceCategory.SYLLABUS
        )

        repository.saveSource(sourceItem, "/path/to/syllabus.pdf")

        val sources = repository.getAllSources()
        sources.size shouldBe 1
        sources[0].title shouldBe "syllabus.pdf"
        sources[0].originUri shouldBe "/path/to/syllabus.pdf"
        sources[0].category shouldBe "SYLLABUS"

        val fragments = repository.getFragmentsForSource("syllabus.pdf")
        fragments.size shouldBe 1
        fragments[0].text shouldBe "Line 1 of syllabus"
        fragments[0].pageNumber shouldBe 1
        fragments[0].sectionTitle shouldBe "Introduction"
    }

    test("updateSourceMetadata stores metadata correctly") {
        val sourceItem = SourceItem(
            title = "syllabus.pdf",
            fragments = listOf(SourceFragment("text")),
            category = SourceCategory.SYLLABUS
        )
        repository.saveSource(sourceItem, "/path/to/syllabus.pdf")

        repository.updateSourceMetadata("syllabus.pdf", "{\"late_policy\": \"no late work\"}")

        val metadata = repository.getSourceMetadata("syllabus.pdf")
        metadata shouldBe "{\"late_policy\": \"no late work\"}"
    }

    test("statusFlow reflects saveSource's initial status and later updateSourceStatus transitions") {
        val sourceItem = SourceItem(
            title = "syllabus.pdf",
            fragments = listOf(SourceFragment("text")),
            category = SourceCategory.SYLLABUS,
            status = SourceStatus.PENDING
        )
        repository.saveSource(sourceItem, null)

        // A late subscriber sees the current phase immediately, not just future transitions —
        // the whole point of using a StateFlow here (see SourceRepository.statusFlow's kdoc).
        repository.statusFlow("syllabus.pdf")?.value shouldBe SourceStatus.PENDING

        repository.updateSourceStatus("syllabus.pdf", SourceStatus.ANALYZING_CONTEXT)
        repository.statusFlow("syllabus.pdf")?.value shouldBe SourceStatus.ANALYZING_CONTEXT
    }

    test("statusFlow is null for a source that has never had a status transition recorded") {
        repository.statusFlow("never-seen") shouldBe null
    }

    test("deleteSource removes source and fragments cascade") {
        val fragment = SourceFragment(
            text = "Line 1 of syllabus",
            pageNumber = 1,
            sectionTitle = "Introduction"
        )
        val sourceItem = SourceItem(
            title = "syllabus.pdf",
            fragments = listOf(fragment),
            category = SourceCategory.SYLLABUS
        )

        repository.saveSource(sourceItem, "/path/to/syllabus.pdf")
        repository.deleteSource("syllabus.pdf")

        repository.getAllSources().size shouldBe 0
        repository.getFragmentsForSource("syllabus.pdf").size shouldBe 0
    }
})
