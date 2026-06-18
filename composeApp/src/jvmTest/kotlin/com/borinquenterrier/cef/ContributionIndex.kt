package com.borinquenterrier.cef

/**
 * Registry of every contributed PDF in the contributions/ directory.
 *
 * ## Rules
 * - Every PR that adds a PDF to contributions/ MUST add a corresponding entry here.
 * - The enum constant name is the public identifier used with -PcontributionFilter=<NAME>.
 * - relativePath is relative to the contributions/ root.
 *
 * ## Naming convention
 *   {SCHOOL}_{SUBJECT}{NUMBER}_{DESCRIPTOR}
 *   where SCHOOL is a short all-caps abbreviation of the institution.
 *
 * ## Running a single file
 *   ./gradlew :composeApp:jvmTest -PcontributionFilter=STLCC_ENG101_WEEKLY
 */
enum class ContributionIndex(
    val relativePath: String,
    val description: String
) {
    // ── Missouri: St. Louis Community College ─────────────────────────────────
    STLCC_CALENDAR(
        "mo/st_louis_community_college/2025-2026/summer/calendar.pdf",
        "STLCC Summer 2026 Academic Calendar"
    ),
    STLCC_ENG101_WEEKLY(
        "mo/st_louis_community_college/2025-2026/summer/101 summer 2026.pdf",
        "STLCC ENG 101-601 Weekly Schedule (week-anchored dates)"
    ),
    STLCC_ENG101_SYLLABUS(
        "mo/st_louis_community_college/2025-2026/summer/syllabi-202620-eng-101-601-20366.pdf",
        "STLCC ENG 101-601 Full Syllabus"
    ),

    // ── Texas: University of Texas at Austin ──────────────────────────────────
    UT_BIO325_GENETICS(
        "tx/ut_austin/2025-2026/fall/BIO325_genetics.pdf",
        "UT Austin BIO 325 Genetics"
    ),
    UT_BIO325L_GENETICS_LAB(
        "tx/ut_austin/2025-2026/fall/BIO325L_genetics_lab.pdf",
        "UT Austin BIO 325L Genetics Lab"
    ),
    UT_BIO337_RESEARCH_METHODS(
        "tx/ut_austin/2025-2026/fall/BIO337_research_methods.pdf",
        "UT Austin BIO 337 Research Methods"
    ),
    UT_E373M_EARLY_GLOBALISMS(
        "tx/ut_austin/2025-2026/fall/E373M_early_globalisms.pdf",
        "UT Austin E 373M Early Globalisms"
    ),
    UT_E375L_VICTORIAN_LIT(
        "tx/ut_austin/2025-2026/fall/E375L_victorian_literature.pdf",
        "UT Austin E 375L Victorian Literature"
    ),
    UT_E376S_AFRICAN_AMERICAN_LIT(
        "tx/ut_austin/2025-2026/fall/E376S_african_american_lit.pdf",
        "UT Austin E 376S African American Literature"
    ),
    UT_E379_AMERICAN_LIT(
        "tx/ut_austin/2025-2026/fall/E379_american_literature.pdf",
        "UT Austin E 379 American Literature"
    ),
    UT_HIS368S_AGE_OF_SAMURAI(
        "tx/ut_austin/2025-2026/fall/HIS368S_age_of_samurai.pdf",
        "UT Austin HIS 368S Age of Samurai"
    ),
    UT_HIS374C_RACE_MIGRATION(
        "tx/ut_austin/2025-2026/fall/HIS374C_race_migration.pdf",
        "UT Austin HIS 374C Race and Migration"
    ),
    UT_HIS378W_CAPSTONE(
        "tx/ut_austin/2025-2026/fall/HIS378W_capstone.pdf",
        "UT Austin HIS 378W History Capstone"
    ),
    UT_M408N_DIFFERENTIAL_CALCULUS(
        "tx/ut_austin/2025-2026/fall/M408N_differential_calculus.pdf",
        "UT Austin M 408N Differential Calculus"
    ),
    UT_M427J_DIFFERENTIAL_EQUATIONS(
        "tx/ut_austin/2025-2026/fall/M427J_differential_equations.pdf",
        "UT Austin M 427J Differential Equations"
    ),
    UT_M427L_ADVANCED_CALCULUS(
        "tx/ut_austin/2025-2026/fall/M427L_advanced_calculus.pdf",
        "UT Austin M 427L Advanced Calculus"
    );
}
