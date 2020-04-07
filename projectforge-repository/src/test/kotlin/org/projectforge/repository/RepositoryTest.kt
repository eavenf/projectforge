/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2020 Micromata GmbH, Germany (www.micromata.com)
//
// ProjectForge is dual-licensed.
//
// This community edition is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License as published
// by the Free Software Foundation; version 3 of the License.
//
// This community edition is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
// Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, see http://www.gnu.org/licenses/.
//
/////////////////////////////////////////////////////////////////////////////

package org.projectforge.repository

import mu.KotlinLogging
import org.apache.jackrabbit.commons.JcrUtils
import org.junit.jupiter.api.*
import java.io.File

private val log = KotlinLogging.logger {}

class RepositoryTest {

    companion object {
        private lateinit var repoService: RepositoryService
        private val repoDir = createTempDir()

        @BeforeAll
        @JvmStatic
        fun setUp() {
            repoService = RepositoryService()
            repoService.init(mapOf(JcrUtils.REPOSITORY_URI to repoDir.toURI().toString()))
            // repoDir.deleteOnExit() // Doesn't work reliable.
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            log.info { "Deleting JackRabbit test repo: $repoDir." }
            Assertions.assertTrue(repoDir.deleteRecursively(), "Couldn't delte JackRabbit test repo: $repoDir.")
        }
    }

    @Test
    fun test() {
        try {
            repoService.ensureNode("world/europe", "germany")
            fail("Exception expected, because node 'world/europe' doesn't exist.")
        } catch(ex: Exception) {
            // OK, hello/world doesn't exist.
        }
        Assertions.assertEquals("/world/europe", repoService.ensureNode(null, "world/europe"))
        repoService.storeProperty("world/europe", "germany", "key", "value")
        Assertions.assertEquals("value", repoService.retrieveProperty("world/europe/", "germany","key"))

       /* val path = repoService.ensureNode("world/europe", "germany/id")
        println(path)
        repoService.store(path)
        repoService.retrieve("world/europe/germany/id")*/
    }
}
