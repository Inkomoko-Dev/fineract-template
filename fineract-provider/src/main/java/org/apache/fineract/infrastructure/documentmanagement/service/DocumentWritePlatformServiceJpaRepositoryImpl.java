/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.infrastructure.documentmanagement.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import org.apache.fineract.infrastructure.codes.domain.CodeValue;
import org.apache.fineract.infrastructure.codes.domain.CodeValueRepositoryWrapper;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.documentmanagement.api.DocumentApiConstant;
import org.apache.fineract.infrastructure.documentmanagement.command.DocumentCommand;
import org.apache.fineract.infrastructure.documentmanagement.command.DocumentCommandValidator;
import org.apache.fineract.infrastructure.documentmanagement.contentrepository.ContentRepository;
import org.apache.fineract.infrastructure.documentmanagement.contentrepository.ContentRepositoryFactory;
import org.apache.fineract.infrastructure.documentmanagement.domain.Document;
import org.apache.fineract.infrastructure.documentmanagement.domain.DocumentRepository;
import org.apache.fineract.infrastructure.documentmanagement.domain.StorageType;
import org.apache.fineract.infrastructure.documentmanagement.exception.ContentManagementException;
import org.apache.fineract.infrastructure.documentmanagement.exception.DocumentNotFoundException;
import org.apache.fineract.infrastructure.documentmanagement.exception.InvalidEntityTypeForDocumentManagementException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.note.domain.Note;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentWritePlatformServiceJpaRepositoryImpl implements DocumentWritePlatformService {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentWritePlatformServiceJpaRepositoryImpl.class);
    private static final DateTimeFormatter AUDIT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PlatformSecurityContext context;
    private final DocumentRepository documentRepository;
    private final ContentRepositoryFactory contentRepositoryFactory;
    private final CodeValueRepositoryWrapper codeValueRepository;
    private final NoteRepository noteRepository;
    private final ClientRepositoryWrapper clientRepositoryWrapper;
    private final LoanRepositoryWrapper loanRepositoryWrapper;
    private final SavingsAccountRepositoryWrapper savingsAccountRepositoryWrapper;

    @Autowired
    public DocumentWritePlatformServiceJpaRepositoryImpl(final PlatformSecurityContext context, final DocumentRepository documentRepository,
            final ContentRepositoryFactory documentStoreFactory, CodeValueRepositoryWrapper codeValueRepository,
            final NoteRepository noteRepository, final ClientRepositoryWrapper clientRepositoryWrapper,
            final LoanRepositoryWrapper loanRepositoryWrapper, final SavingsAccountRepositoryWrapper savingsAccountRepositoryWrapper) {
        this.context = context;
        this.documentRepository = documentRepository;
        this.contentRepositoryFactory = documentStoreFactory;
        this.codeValueRepository = codeValueRepository;
        this.noteRepository = noteRepository;
        this.clientRepositoryWrapper = clientRepositoryWrapper;
        this.loanRepositoryWrapper = loanRepositoryWrapper;
        this.savingsAccountRepositoryWrapper = savingsAccountRepositoryWrapper;
    }

    @Transactional
    @Override
    public Long createDocument(final DocumentCommand documentCommand, final InputStream inputStream) {
        try {
            this.context.authenticatedUser();

            final DocumentCommandValidator validator = new DocumentCommandValidator(documentCommand);

            validateParentEntityType(documentCommand);

            validator.validateForCreate();
            if ((documentCommand.getKivaProfileImage() != null && documentCommand.getKivaProfileImage())
                    && !(documentCommand.getType().equals("image/png") || documentCommand.getType().equals("image/PNG")
                            || documentCommand.getType().equals("image/jpg") || documentCommand.getType().equals("image/JPG")
                            || documentCommand.getType().equals("image/jpeg") || documentCommand.getType().equals("image/JPEG")
                            || documentCommand.getType().equals("image/GIF") || documentCommand.getType().equals("image/gif"))) {
                throw new GeneralPlatformDomainRuleException("error.msg.document.invalid.file.type",
                        "Invalid file type for Kiva Profile Image required file type is PNG, JPG, JPEG, GIF");
            }
            // Check if file type is application/octet-stream and attempt to detect the MIME type
            if (documentCommand.getType().equals("application/octet-stream")) {
                Tika tika = new Tika();
                // Use Tika to detect the MIME type based on the file content
                String detectedMimeType = tika.detect(inputStream);

                // Log or handle the detected MIME type
                LOG.info("Detected MIME type: " + detectedMimeType);

                // Update the documentCommand type to the detected MIME type
                documentCommand.setType(detectedMimeType);
            }

            final ContentRepository contentRepository = this.contentRepositoryFactory.getRepository();

            final String fileLocation = contentRepository.saveFile(inputStream, documentCommand);
            CodeValue documentType = null;
            final Long documentTypeId = documentCommand.getDocumentType();
            if (documentTypeId != null) {
                documentType = this.codeValueRepository.findOneByCodeNameAndIdWithNotFoundDetection(DocumentApiConstant.DOCUMENT_TYPE,
                        documentTypeId);
            }
            final Document document = Document.createNew(documentCommand.getParentEntityType(), documentCommand.getParentEntityId(),
                    documentCommand.getName(), documentCommand.getFileName(), documentCommand.getSize(), documentCommand.getType(),
                    documentCommand.getDescription(), fileLocation, contentRepository.getStorageType(),
                    documentCommand.getKivaProfileImage(), documentType);

            this.documentRepository.saveAndFlush(document);

            return document.getId();
        } catch (final JpaSystemException | DataIntegrityViolationException dve) {
            LOG.error("Error occured.", dve);
            throw new PlatformDataIntegrityException("error.msg.document.unknown.data.integrity.issue",
                    "Unknown data integrity issue with resource.", dve);
        } catch (IOException e) {
            LOG.error("Error occurred while detecting MIME type.", e);
            throw new GeneralPlatformDomainRuleException("error.msg.document.mime.type.detection.failed", "MIME type detection failed", e);
        }
    }

    @Transactional
    @Override
    public Long createInternalDocument(final String entityType, final Long entityId, final Long fileSize, final InputStream inputStream,
            final String mimeType, final String name, final String description, final String fileName) {

        final DocumentCommand documentCommand = new DocumentCommand(null, null, entityType, entityId, name, fileName, fileSize, mimeType,
                description, null, false);

        final Long documentId = createDocument(documentCommand, inputStream);

        return documentId;

    }

    @Transactional
    @Override
    public CommandProcessingResult updateDocument(final DocumentCommand documentCommand, final InputStream inputStream) {
        try {
            final AppUser currentUser = this.context.authenticatedUser();

            String oldLocation = null;
            final DocumentCommandValidator validator = new DocumentCommandValidator(documentCommand);
            validator.validateForUpdate();
            // TODO check if entity id is valid and within data scope for the
            // user
            final Document documentForUpdate = this.documentRepository.findById(documentCommand.getId())
                    .orElseThrow(() -> new DocumentNotFoundException(documentCommand.getParentEntityType(),
                            documentCommand.getParentEntityId(), documentCommand.getId()));

            // Capture the previous name for audit trail
            final String previousName = documentForUpdate.getName();

            final StorageType documentStoreType = documentForUpdate.storageType();
            oldLocation = documentForUpdate.getLocation();
            if (inputStream != null && documentCommand.isFileNameChanged()) {
                final ContentRepository contentRepository = this.contentRepositoryFactory.getRepository();
                documentCommand.setLocation(contentRepository.saveFile(inputStream, documentCommand));
                documentCommand.setStorageType(contentRepository.getStorageType().getValue());
            }

            documentForUpdate.update(documentCommand);

            if (inputStream != null && documentCommand.isFileNameChanged()) {
                final ContentRepository contentRepository = this.contentRepositoryFactory.getRepository(documentStoreType);
                contentRepository.deleteFile(oldLocation);
            }

            this.documentRepository.saveAndFlush(documentForUpdate);

            // Create audit trail note if document name was changed
            if (documentCommand.isNameChanged() && previousName != null && !previousName.equals(documentCommand.getName())) {
                createDocumentRenameAuditNote(documentForUpdate.getParentEntityType(), documentForUpdate.getParentEntityId(),
                        previousName, documentCommand.getName(), currentUser);
            }

            return new CommandProcessingResult(documentForUpdate.getId());
        } catch (final JpaSystemException | DataIntegrityViolationException dve) {
            LOG.error("Error occured.", dve);
            throw new PlatformDataIntegrityException("error.msg.document.unknown.data.integrity.issue",
                    "Unknown data integrity issue with resource.", dve);
        } catch (final ContentManagementException cme) {
            LOG.error("Error occured.", cme);
            throw new ContentManagementException(documentCommand.getName(), cme.getMessage(), cme);
        }
    }

    @Transactional
    @Override
    public CommandProcessingResult deleteDocument(final DocumentCommand documentCommand) {
        this.context.authenticatedUser();

        validateParentEntityType(documentCommand);
        // TODO: Check document is present under this entity Id
        final Document document = this.documentRepository.findById(documentCommand.getId())
                .orElseThrow(() -> new DocumentNotFoundException(documentCommand.getParentEntityType(), documentCommand.getParentEntityId(),
                        documentCommand.getId()));
        this.documentRepository.delete(document);

        final ContentRepository contentRepository = this.contentRepositoryFactory.getRepository(document.storageType());
        contentRepository.deleteFile(document.getLocation());
        return new CommandProcessingResult(document.getId());
    }

    private void validateParentEntityType(final DocumentCommand documentCommand) {
        if (!checkValidEntityType(documentCommand.getParentEntityType())) {
            throw new InvalidEntityTypeForDocumentManagementException(documentCommand.getParentEntityType());
        }
    }

    /**
     * Creates an audit trail note when a document is renamed.
     * The note is attached to the parent entity (Client, Loan, or Savings Account)
     * and includes the previous name, new name, user, and timestamp.
     *
     * @param parentEntityType The type of parent entity (CLIENTS, LOANS, SAVINGS, etc.)
     * @param parentEntityId The ID of the parent entity
     * @param previousName The previous document name
     * @param newName The new document name
     * @param user The user who performed the rename
     */
    private void createDocumentRenameAuditNote(final String parentEntityType, final Long parentEntityId,
            final String previousName, final String newName, final AppUser user) {
        try {
            final String timestamp = DateUtils.getLocalDateTimeOfTenant().format(AUDIT_DATE_FORMATTER);
            final String userName = user.getUsername();
            final String auditNoteText = String.format("Document renamed: '%s' → '%s' | By: %s | At: %s",
                    previousName, newName, userName, timestamp);

            Note auditNote = null;

            if (DocumentManagementEntity.CLIENTS.name().equalsIgnoreCase(parentEntityType)) {
                final Client client = this.clientRepositoryWrapper.findOneWithNotFoundDetection(parentEntityId);
                auditNote = new Note(client, auditNoteText);
            } else if (DocumentManagementEntity.LOANS.name().equalsIgnoreCase(parentEntityType)) {
                final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(parentEntityId);
                auditNote = Note.loanNote(loan, auditNoteText);
            } else if (DocumentManagementEntity.SAVINGS.name().equalsIgnoreCase(parentEntityType)) {
                final SavingsAccount savingsAccount = this.savingsAccountRepositoryWrapper.findOneWithNotFoundDetection(parentEntityId);
                auditNote = Note.savingNote(savingsAccount, auditNoteText);
            }

            if (auditNote != null) {
                this.noteRepository.saveAndFlush(auditNote);
                LOG.info("Document rename audit note created for {} with ID {}: {}", parentEntityType, parentEntityId, auditNoteText);
            }
        } catch (Exception e) {
            // Log the error but don't fail the document update operation
            LOG.warn("Failed to create audit note for document rename on {} with ID {}: {}",
                    parentEntityType, parentEntityId, e.getMessage());
        }
    }

    private static boolean checkValidEntityType(final String entityType) {
        for (final DocumentManagementEntity entities : DocumentManagementEntity.values()) {
            if (entities.name().equalsIgnoreCase(entityType)) {
                return true;
            }
        }
        return false;
    }

    /*** Entities for document Management **/
    public enum DocumentManagementEntity {

        CLIENTS, CLIENT_IDENTIFIERS, STAFF, LOANS, SAVINGS, GROUPS, IMPORT, BUSINESS_OWNERS, RECURRING;

        @Override
        public String toString() {
            return name().toString().toLowerCase();
        }
    }
}
