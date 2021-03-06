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

package org.projectforge.framework.jcr

import mu.KotlinLogging
import org.projectforge.SystemStatus
import org.projectforge.business.user.UserGroupCache
import org.projectforge.framework.persistence.api.BaseDao
import org.projectforge.framework.persistence.api.ExtendedBaseDO
import org.projectforge.framework.persistence.api.IdObject
import org.projectforge.framework.persistence.entities.DefaultBaseDO
import org.projectforge.framework.persistence.jpa.PfEmgrFactory
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext
import org.projectforge.framework.utils.NumberHelper
import org.projectforge.jcr.FileObject
import org.projectforge.jcr.RepoService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.InputStream

private val log = KotlinLogging.logger {}

/**
 * Service for handling attachments of DO's. It's possible to attach files to each [IdObject].
 */
@Service
open class AttachmentsService {
    @Autowired
    private lateinit var emgrFactory: PfEmgrFactory

    @Autowired
    private lateinit var repoService: RepoService

    /**
     * @param path Unique path of data object.
     * @param id Id of data object.
     */
    @JvmOverloads
    open fun getAttachments(path: String, id: Any, accessChecker: AttachmentsAccessChecker?, subPath: String? = null): List<Attachment>? {
        accessChecker?.checkSelectAccess(ThreadLocalUserContext.getUser(), path = path, id = id, subPath = subPath)
        return repoService.getFileInfos(getPath(path, id), subPath ?: DEFAULT_NODE)?.map { createAttachment(it) }
    }

    /**
     * @param path Unique path of data object.
     * @param id Id of data object.
     */
    @JvmOverloads
    open fun getAttachmentInfo(path: String, id: Any, fileId: String, accessChecker: AttachmentsAccessChecker, subPath: String? = null): Attachment? {
        accessChecker.checkSelectAccess(ThreadLocalUserContext.getUser(), path = path, id = id, subPath = subPath)
        val fileObject = repoService.getFileInfo(
                getPath(path, id),
                subPath ?: DEFAULT_NODE,
                fileId = fileId)
                ?: return null
        return createAttachment(fileObject)
    }

    /**
     * @param path Unique path of data object.
     * @param id Id of data object.
     */
    @JvmOverloads
    open fun getAttachmentContent(path: String, id: Any, fileId: String, accessChecker: AttachmentsAccessChecker, subPath: String? = null): ByteArray? {
        accessChecker.checkDownloadAccess(ThreadLocalUserContext.getUser(), path = path, id = id, fileId = fileId, subPath = subPath)
        val fileObject = repoService.getFileInfo(
                getPath(path, id),
                subPath ?: DEFAULT_NODE,
                fileId = fileId)
                ?: return null
        return if (repoService.retrieveFile(fileObject)) {
            fileObject.content
        } else {
            null
        }
    }

    /**
     * @param path Unique path of data object.
     * @param id Id of data object.
     */
    @JvmOverloads
    open fun getAttachmentInputStream(path: String, id: Any, fileId: String, accessChecker: AttachmentsAccessChecker, subPath: String? = null)
            : Pair<FileObject, InputStream>? {
        accessChecker.checkDownloadAccess(ThreadLocalUserContext.getUser(), path = path, id = id, fileId = fileId, subPath = subPath)
        val fileObject = repoService.getFileInfo(
                getPath(path, id),
                subPath ?: DEFAULT_NODE,
                fileId = fileId)
        val inputStream = if (fileObject != null) {
            repoService.retrieveFileInputStream(fileObject)
        } else {
            null
        }
        if (fileObject == null || inputStream == null) {
            log.error { "Can't download file of ${getPath(path, id)} #$fileId, because user has no access to this object or it doesn't exist." }
            return null
        }
        return Pair(fileObject, inputStream)
    }

    /**
     * @param path Unique path of data object.
     * @param id Id of data object.
     */
    @JvmOverloads
    open fun addAttachment(path: String,
                           id: Any,
                           fileName: String?,
                           content: ByteArray,
                           enableSearchIndex: Boolean,
                           accessChecker: AttachmentsAccessChecker,
                           subPath: String? = null): Attachment {
        accessChecker.checkUploadAccess(ThreadLocalUserContext.getUser(), path = path, id = id, subPath = subPath)
        val fileObject = FileObject(
                getPath(path, id),
                subPath ?: DEFAULT_NODE,
                fileName = fileName)
        developerWarning(path, id, "addAttachment", enableSearchIndex)
        fileObject.content = content
        repoService.storeFile(fileObject, ThreadLocalUserContext.getUserId()!!.toString())
        return createAttachment(fileObject)
    }

    /**
     * @param path Unique path of data object.
     */
    @JvmOverloads
    open fun addAttachment(path: String,
                           fileName: String?,
                           content: ByteArray,
                           baseDao: BaseDao<out ExtendedBaseDO<Int>>,
                           obj: ExtendedBaseDO<Int>,
                           accessChecker: AttachmentsAccessChecker,
                           subPath: String? = null)
            : Attachment {
        accessChecker.checkUploadAccess(ThreadLocalUserContext.getUser(), path = path, id = obj.id, subPath = subPath)
        val attachment = addAttachment(path, obj.id, fileName, content, false, accessChecker, subPath)
        updateAttachmentsInfo(path, baseDao, obj, subPath)
        return attachment
    }

    /**
     * @param path Unique path of data object.
     * @param id Id of data object.
     */
    @JvmOverloads
    open fun addAttachment(path: String,
                           id: Any,
                           fileName: String?,
                           inputStream: InputStream,
                           enableSearchIndex: Boolean,
                           accessChecker: AttachmentsAccessChecker,
                           subPath: String? = null): Attachment {
        developerWarning(path, id, "addAttachment", enableSearchIndex)
        accessChecker.checkUploadAccess(ThreadLocalUserContext.getUser(), path = path, id = id, subPath = subPath)
        repoService.ensureNode(null, getPath(path, id))
        val fileObject = FileObject(getPath(path, id), subPath ?: DEFAULT_NODE, fileName = fileName)
        repoService.storeFile(fileObject, inputStream, ThreadLocalUserContext.getUserId()!!.toString())
        return createAttachment(fileObject)
    }

    /**
     * @param path Unique path of data object.
     */
    @JvmOverloads
    open fun addAttachment(path: String,
                           fileName: String?,
                           inputStream: InputStream,
                           baseDao: BaseDao<out ExtendedBaseDO<Int>>,
                           obj: ExtendedBaseDO<Int>,
                           accessChecker: AttachmentsAccessChecker,
                           subPath: String? = null)
            : Attachment {
        accessChecker.checkUploadAccess(ThreadLocalUserContext.getUser(), path = path, id = obj.id, subPath = subPath)
        val attachment = addAttachment(path, obj.id, fileName, inputStream, false, accessChecker, subPath)
        updateAttachmentsInfo(path, baseDao, obj, subPath)
        if (obj is DefaultBaseDO && obj is AttachmentsInfo) {
            val dbObj = baseDao.getById(obj.id) as AttachmentsInfo
            dbObj.attachmentsLastUserAction = "Attachment uploaded: '$fileName'."
            baseDao.internalUpdateAny(dbObj)
        }
        return attachment
    }

    /**
     * @param path Unique path of data object.
     * @param id Id of data object.
     */
    @JvmOverloads
    open fun deleteAttachment(path: String,
                              id: Any,
                              fileId: String,
                              enableSearchIndex: Boolean,
                              accessChecker: AttachmentsAccessChecker,
                              subPath: String? = null)
            : Boolean {
        developerWarning(path, id, "deleteAttachment", enableSearchIndex)
        accessChecker.checkDeleteAccess(ThreadLocalUserContext.getUser(), path = path, id = id, fileId = fileId, subPath = subPath)
        val fileObject = FileObject(getPath(path, id), subPath ?: DEFAULT_NODE, fileId = fileId)
        return repoService.deleteFile(fileObject)
    }

    /**
     * @param path Unique path of data object.
     */
    @JvmOverloads
    open fun deleteAttachment(path: String,
                              fileId: String,
                              baseDao: BaseDao<out ExtendedBaseDO<Int>>,
                              obj: ExtendedBaseDO<Int>,
                              accessChecker: AttachmentsAccessChecker,
                              subPath: String? = null)
            : Boolean {
        accessChecker.checkDeleteAccess(ThreadLocalUserContext.getUser(), path = path, id = obj.id, fileId = fileId, subPath = subPath)
        val fileObject = FileObject(getPath(path, obj.id), subPath ?: DEFAULT_NODE, fileId = fileId)
        val result = repoService.deleteFile(fileObject)
        if (result) {
            if (obj is DefaultBaseDO && obj is AttachmentsInfo) {
                val dbObj = baseDao.getById(obj.id) as AttachmentsInfo
                dbObj.attachmentsLastUserAction = "Attachment '${fileObject.fileName}' deleted."
                baseDao.internalUpdateAny(dbObj)
            }
            updateAttachmentsInfo(path, baseDao, obj, subPath)
        }
        return result
    }

    /**
     * @param path Unique path of data object.
     * @param id Id of data object.
     */
    @JvmOverloads
    open fun changeFileInfo(path: String,
                            id: Any,
                            fileId: String,
                            enableSearchIndex: Boolean,
                            newFileName: String?,
                            newDescription: String?,
                            accessChecker: AttachmentsAccessChecker,
                            subPath: String? = null)
            : FileObject? {
        developerWarning(path, id, "changeProperty", enableSearchIndex)
        accessChecker.checkUpdateAccess(ThreadLocalUserContext.getUser(), path = path, id = id, fileId = fileId, subPath = subPath)
        val fileObject = FileObject(getPath(path, id), subPath ?: DEFAULT_NODE, fileId = fileId)
        return repoService.changeFileInfo(fileObject, user = ThreadLocalUserContext.getUserId()!!.toString(), newFileName = newFileName, newDescription = newDescription)
    }

    /**
     * @param path Unique path of data object.
     */
    @JvmOverloads
    open fun changeFileInfo(path: String,
                            fileId: String,
                            baseDao: BaseDao<out ExtendedBaseDO<Int>>,
                            obj: ExtendedBaseDO<Int>,
                            newFileName: String?,
                            newDescription: String?,
                            accessChecker: AttachmentsAccessChecker,
                            subPath: String? = null)
            : FileObject? {
        accessChecker.checkUpdateAccess(ThreadLocalUserContext.getUser(), path = path, id = obj.id, fileId = fileId, subPath = subPath)
        val fileObject = FileObject(getPath(path, obj.id), subPath ?: DEFAULT_NODE, fileId = fileId)
        val result = repoService.changeFileInfo(fileObject, ThreadLocalUserContext.getUserId()!!.toString(), newFileName, newDescription)
        if (result != null) {
            val fileNameChanged = if (!newFileName.isNullOrBlank()) "filename='$newFileName'" else null
            val descriptionChanged = if (newDescription != null) "description='$newDescription'" else null
            if (obj is DefaultBaseDO && obj is AttachmentsInfo) {
                val dbObj = baseDao.getById(obj.id) as AttachmentsInfo
                dbObj.attachmentsLastUserAction = "Attachment infos changed of file '${result.fileName}': ${fileNameChanged ?: " "}${descriptionChanged ?: ""}".trim()
                baseDao.internalUpdateAny(dbObj)
            }
            updateAttachmentsInfo(path, baseDao, obj, subPath)
        }
        return result
    }

    /**
     * Path will be path/id.
     * @return path relative to main node ProjectForge.
     */
    open fun getPath(path: String, id: Any): String {
        return "$path/$id"
    }

    private fun updateAttachmentsInfo(path: String,
                                      baseDao: BaseDao<out ExtendedBaseDO<Int>>,
                                      obj: ExtendedBaseDO<Int>,
                                      subPath: String? = null) {
        if (obj !is AttachmentsInfo) {
            return // Nothing to do.
        }
        val dbObj = baseDao.getById(obj.id)
        if (dbObj is AttachmentsInfo) {
            // TODO: multiple subPath support (all attachments of all lists should be used for indexing).
            if (subPath != null) {
                log.warn("********* Support of multiple lists in attachments not yet supported by search index.")
            }
            val attachments = getAttachments(path, obj.id, null)//, subPath)
            if (attachments != null) {
                dbObj.attachmentsNames = attachments.joinToString(separator = " ") { "${it.name}" }
                dbObj.attachmentsIds = attachments.joinToString(separator = " ") { "${it.fileId}" }
                dbObj.attachmentsSize = attachments.size
            } else {
                dbObj.attachmentsNames = null
                dbObj.attachmentsIds = null
                dbObj.attachmentsSize = null
            }
            baseDao.updateAny(dbObj)
        } else {
            val msg = "Can't update search index of ${dbObj::class.java.name}. Dear developer, it's not of type ${AttachmentsInfo::class.java.name}!"
            if (SystemStatus.isDevelopmentMode()) {
                throw UnsupportedOperationException(msg)
            }
            log.warn { msg }
        }
    }

    private fun createAttachment(fileObject: FileObject): Attachment {
        val attachment = Attachment(fileObject)
        NumberHelper.parseInteger(fileObject.createdByUser)?.let {
            attachment.createdByUser = UserGroupCache.tenantInstance.getUser(it)?.getFullname()
        }
        NumberHelper.parseInteger(fileObject.lastUpdateByUser)?.let {
            attachment.lastUpdateByUser = UserGroupCache.tenantInstance.getUser(it)?.getFullname()
        }
        return attachment
    }

    private fun developerWarning(path: String, id: Any, method: String, enableSearchIndex: Boolean) {
        if (enableSearchIndex) {
            val msg = "Can't update search index of ${getPath(path, id)}. Dear developer, call method '$method' with data object and baseDao instead!"
            if (SystemStatus.isDevelopmentMode()) {
                throw UnsupportedOperationException(msg)
            }
            log.warn { msg }
        }
    }

    companion object {
        const val DEFAULT_NODE = "attachments"
    }
}
