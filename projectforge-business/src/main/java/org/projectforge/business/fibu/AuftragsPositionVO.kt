/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2019 Micromata GmbH, Germany (www.micromata.com)
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

package org.projectforge.business.fibu

import org.apache.commons.lang3.builder.HashCodeBuilder

import java.io.Serializable
import java.math.BigDecimal
import java.util.Objects

/**
 * Repräsentiert einee Position innerhalb eines Auftrags als Übersichtsobject (value object) zur Verwendung z. B. im TaskTree.
 * @author Kai Reinhard (k.reinhard@micromata.de)
 */
class AuftragsPositionVO(auftragsPosition: AuftragsPositionDO) : Comparable<AuftragsPositionVO>, Serializable {

    val number: Short

    var auftragId: Int? = null
        private set

    var auftragNummer: Int? = null
        private set

    /**
     * @see AuftragDO.getTitel
     */
    var auftragTitle: String? = null
        private set

    /**
     * @see AuftragDO.getAuftragsStatus
     */
    var auftragsStatus: AuftragsStatus? = null
        private set

    /**
     * @see AuftragDO.getPersonDays
     */
    var auftragsPersonDays: BigDecimal? = null
        private set

    val taskId: Int?

    val art: AuftragsPositionsArt?

    val status: AuftragsPositionsStatus?

    val titel: String?

    val nettoSumme: BigDecimal?

    var personDays: BigDecimal? = null
        private set

    val isVollstaendigFakturiert: Boolean

    init {
        val auftrag = auftragsPosition.auftrag
        this.number = auftragsPosition.number
        if (auftrag != null) { // Should be always true.
            this.auftragId = auftrag.id
            this.auftragNummer = auftrag.nummer
            this.auftragTitle = auftrag.titel
            this.auftragsStatus = auftrag.auftragsStatus
            this.auftragsPersonDays = auftrag.personDays
        }
        this.taskId = auftragsPosition.taskId
        this.art = auftragsPosition.art
        this.status = auftragsPosition.status
        this.titel = auftragsPosition.titel
        this.nettoSumme = auftragsPosition.nettoSumme
        this.personDays = auftragsPosition.personDays
        if (this.personDays == null) {
            this.personDays = BigDecimal.ZERO
        }
        this.isVollstaendigFakturiert = auftragsPosition.vollstaendigFakturiert!!
    }

    override fun equals(o: Any?): Boolean {
        if (o is AuftragsPositionVO) {
            val other = o as AuftragsPositionVO?
            if (this.number != other!!.number)
                return false
            return if (this.auftragId != other.auftragId) false else true
        }
        return false
    }

    override fun hashCode(): Int {
        val hcb = HashCodeBuilder()
        hcb.append(number)
        hcb.append(auftragId)
        return hcb.toHashCode()
    }

    override fun compareTo(o: AuftragsPositionVO): Int {
        return if (this.auftragNummer != o.auftragNummer) {
            this.auftragNummer!!.compareTo(o.auftragNummer!!)
        } else java.lang.Short.compare(this.number, o.number)
    }

    companion object {
        private const val serialVersionUID = 5425767060569257885L
    }
}
