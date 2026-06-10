package com.borinquenterrier.cef

import com.borinquenterrier.cef.db.DriverFactory

/**
2:  * A DriverFactory subclass used on the server to route a specific student's
3:  * database connection to their isolated sharded SQLite path.
4:  */
class TenantDriverFactory(
    studentId: String,
    dbFactory: TenantDatabaseFactory
) : DriverFactory(dbFile = dbFactory.dbFileFor(studentId))
