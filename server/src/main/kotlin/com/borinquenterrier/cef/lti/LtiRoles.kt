package com.borinquenterrier.cef.lti

/**
 * IMS role URIs (https://www.imsglobal.org/spec/lti/v1p3#role-vocabularies) come in several
 * flavors — institution role, system role, context role — each with their own "Instructor"/
 * "Administrator" URI. Rather than enumerating every exact URI, treat any role URI containing
 * "Instructor" or "Administrator" as staff — a deliberate simplification documented in
 * docs/adr/0007-staff-console-via-lti-roles.md, not an oversight.
 */
fun isStaffRole(roleUris: List<String>): Boolean =
    roleUris.any { it.contains("Instructor", ignoreCase = true) || it.contains("Administrator", ignoreCase = true) }
