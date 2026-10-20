package com.ailegal.service.impl;

import com.ailegal.dto.*;
import com.ailegal.entity.LegalArticle;
import com.ailegal.entity.LegalDocument;
import com.ailegal.exception.BusinessException;
import com.ailegal.mapper.LegalArticleMapper;
import com.ailegal.mapper.LegalDocumentMapper;
import com.ailegal.service.LegalKnowledgeService;
import com.ailegal.service.OperationLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LegalKnowledgeServiceImpl implements LegalKnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(LegalKnowledgeServiceImpl.class);

    // Matches "第X条" at start of line (with optional leading whitespace)
    // X can be Chinese numerals (一～九百九十九) or Arabic numerals
    private static final Pattern ARTICLE_PATTERN = Pattern.compile(
            "(?:^|\\n)\\s*(第[一二三四五六七八九十百千零]+条|第\\d+条)\\s*"
    );

    private final LegalDocumentMapper documentMapper;
    private final LegalArticleMapper articleMapper;
    private final OperationLogService operationLogService;

    public LegalKnowledgeServiceImpl(LegalDocumentMapper documentMapper,
                                     LegalArticleMapper articleMapper,
                                     OperationLogService operationLogService) {
        this.documentMapper = documentMapper;
        this.articleMapper = articleMapper;
        this.operationLogService = operationLogService;
    }

    @Override
    @Transactional
    public DocumentResponse createDocument(DocumentCreateRequest request,
                                           Long userId, String username, String ip) {
        LegalDocument doc = new LegalDocument();
        doc.setDocumentName(request.getDocumentName());
        doc.setDomain(request.getDomain());
        doc.setDocumentType(request.getDocumentType());
        doc.setEffectiveDate(parseDate(request.getEffectiveDate()));
        doc.setSource(request.getSource() != null ? request.getSource() : "");
        doc.setValidStatus(1);
        doc.setArticleCount(0);
        doc.setVectorStatus("NONE");
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        documentMapper.insert(doc);

        operationLogService.log(userId, username, "LEGAL_DOC_CREATE",
                doc.getDocumentName(), "创建成功，id=" + doc.getId(), ip);

        return DocumentResponse.fromEntity(doc);
    }

    @Override
    public DocumentResponse getDocument(Long id) {
        LegalDocument doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException(404, "文档不存在");
        }
        return DocumentResponse.fromEntity(doc);
    }

    @Override
    public List<DocumentResponse> listDocuments(String domain, String documentType, Integer validStatus) {
        LambdaQueryWrapper<LegalDocument> q = new LambdaQueryWrapper<>();
        if (domain != null && !domain.isBlank()) {
            q.eq(LegalDocument::getDomain, domain);
        }
        if (documentType != null && !documentType.isBlank()) {
            q.eq(LegalDocument::getDocumentType, documentType);
        }
        if (validStatus != null) {
            q.eq(LegalDocument::getValidStatus, validStatus);
        }
        q.orderByDesc(LegalDocument::getUpdatedAt);
        return documentMapper.selectList(q).stream()
                .map(DocumentResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public DocumentResponse updateDocument(Long id, DocumentUpdateRequest request,
                                           Long userId, String username, String ip) {
        LegalDocument doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException(404, "文档不存在");
        }

        boolean domainChanged = false;
        boolean sourceChanged = false;

        if (request.getDocumentName() != null) {
            doc.setDocumentName(request.getDocumentName());
        }
        if (request.getDomain() != null && !request.getDomain().equals(doc.getDomain())) {
            doc.setDomain(request.getDomain());
            domainChanged = true;
        }
        if (request.getDocumentType() != null) {
            doc.setDocumentType(request.getDocumentType());
        }
        if (request.getEffectiveDate() != null) {
            doc.setEffectiveDate(parseDate(request.getEffectiveDate()));
        }
        if (request.getSource() != null && !request.getSource().equals(doc.getSource())) {
            doc.setSource(request.getSource());
            sourceChanged = true;
        }

        doc.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(doc);

        // Sync redundant fields to all articles under this document
        if (domainChanged || sourceChanged) {
            LambdaQueryWrapper<LegalArticle> aq = new LambdaQueryWrapper<>();
            aq.eq(LegalArticle::getDocumentId, doc.getId());
            List<LegalArticle> articles = articleMapper.selectList(aq);
            for (LegalArticle article : articles) {
                if (domainChanged) {
                    article.setDomain(doc.getDomain());
                }
                if (sourceChanged) {
                    article.setSource(doc.getSource());
                }
                article.setUpdatedAt(LocalDateTime.now());
                articleMapper.updateById(article);
            }
        }

        operationLogService.log(userId, username, "LEGAL_DOC_UPDATE",
                doc.getDocumentName(), "修改成功", ip);

        return DocumentResponse.fromEntity(doc);
    }

    @Override
    @Transactional
    public void updateDocumentStatus(Long id, Integer validStatus, Long userId, String username, String ip) {
        LegalDocument doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException(404, "文档不存在");
        }
        Integer oldStatus = doc.getValidStatus();
        doc.setValidStatus(validStatus);
        doc.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(doc);

        operationLogService.log(userId, username, "LEGAL_DOC_STATUS",
                doc.getDocumentName(), "状态由" + oldStatus + "改为" + validStatus, ip);
    }

    @Override
    @Transactional
    public void deleteDocument(Long id, Long userId, String username, String ip) {
        LegalDocument doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BusinessException(404, "文档不存在");
        }

        LambdaQueryWrapper<LegalArticle> aq = new LambdaQueryWrapper<>();
        aq.eq(LegalArticle::getDocumentId, id);
        articleMapper.delete(aq);
        documentMapper.deleteById(id);

        operationLogService.log(userId, username, "LEGAL_DOC_DELETE",
                doc.getDocumentName(), "删除成功，含所属全部条款", ip);
    }

    @Override
    @Transactional
    public ArticleResponse addArticle(Long documentId, ArticleCreateRequest request,
                                      Long userId, String username, String ip) {
        LegalDocument doc = documentMapper.selectById(documentId);
        if (doc == null) {
            throw new BusinessException(404, "文档不存在");
        }

        LegalArticle article = new LegalArticle();
        article.setDocumentId(documentId);
        article.setArticleNumber(request.getArticleNumber());
        article.setContent(request.getContent());
        article.setDomain(doc.getDomain());
        article.setSource(doc.getSource());
        article.setValidStatus(1);
        article.setVectorStatus("NONE");
        article.setCreatedAt(LocalDateTime.now());
        article.setUpdatedAt(LocalDateTime.now());
        articleMapper.insert(article);

        // Update article_count
        recalcArticleCount(documentId);

        operationLogService.log(userId, username, "LEGAL_ARTICLE_CREATE",
                article.getArticleNumber(), "创建成功，id=" + article.getId(), ip);

        return ArticleResponse.fromEntity(article);
    }

    @Override
    @Transactional
    public ArticleResponse updateArticle(Long id, ArticleUpdateRequest request,
                                         Long userId, String username, String ip) {
        LegalArticle article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException(404, "条款不存在");
        }

        if (request.getArticleNumber() != null) {
            article.setArticleNumber(request.getArticleNumber());
        }
        if (request.getContent() != null) {
            article.setContent(request.getContent());
        }
        article.setUpdatedAt(LocalDateTime.now());
        articleMapper.updateById(article);

        operationLogService.log(userId, username, "LEGAL_ARTICLE_UPDATE",
                article.getArticleNumber(), "修改成功", ip);

        return ArticleResponse.fromEntity(article);
    }

    @Override
    @Transactional
    public void updateArticleStatus(Long id, Integer validStatus, Long userId, String username, String ip) {
        LegalArticle article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException(404, "条款不存在");
        }
        Integer oldStatus = article.getValidStatus();
        article.setValidStatus(validStatus);
        article.setUpdatedAt(LocalDateTime.now());
        articleMapper.updateById(article);

        operationLogService.log(userId, username, "LEGAL_ARTICLE_STATUS",
                article.getArticleNumber(), "状态由" + oldStatus + "改为" + validStatus, ip);
    }

    @Override
    @Transactional
    public void deleteArticle(Long id, Long userId, String username, String ip) {
        LegalArticle article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException(404, "条款不存在");
        }

        Long documentId = article.getDocumentId();
        articleMapper.deleteById(id);
        recalcArticleCount(documentId);

        operationLogService.log(userId, username, "LEGAL_ARTICLE_DELETE",
                article.getArticleNumber(), "删除成功", ip);
    }

    @Override
    public List<ArticleResponse> getArticlesByDocumentId(Long documentId) {
        LambdaQueryWrapper<LegalArticle> q = new LambdaQueryWrapper<>();
        q.eq(LegalArticle::getDocumentId, documentId);
        q.orderByAsc(LegalArticle::getId);
        return articleMapper.selectList(q).stream()
                .map(ArticleResponse::fromEntity)
                .toList();
    }

    @Override
    public List<LegalArticleDocument> selectValidArticlesWithDocuments(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return List.of();
        }
        return articleMapper.selectValidArticlesWithDocuments(articleIds);
    }

    @Override
    public List<LegalArticleDocument> selectValidReferenceIds(List<String> documentNames, List<String> articleNumbers) {
        if (documentNames == null || documentNames.isEmpty()
                || articleNumbers == null || articleNumbers.isEmpty()) {
            return List.of();
        }
        return articleMapper.selectValidReferenceIds(documentNames, articleNumbers);
    }

    @Override
    @Transactional
    public TxtImportResult importTxt(MultipartFile file, String documentName, String domain,
                                     String documentType, String effectiveDate, String source,
                                     Long userId, String username, String ip) {
        // Validate file
        if (file.isEmpty()) {
            operationLogService.logFailure(userId, username, "LEGAL_TXT_IMPORT",
                    documentName, "导入失败: 文件为空", ip);
            return TxtImportResult.failure("文件为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".txt")) {
            operationLogService.logFailure(userId, username, "LEGAL_TXT_IMPORT",
                    documentName, "导入失败: 仅支持 .txt 文件", ip);
            throw new BusinessException(400, "仅支持 .txt 文件");
        }

        // Read file content as UTF-8
        String content;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            content = sb.toString();
        } catch (Exception e) {
            log.error("Failed to read TXT file", e);
            operationLogService.logFailure(userId, username, "LEGAL_TXT_IMPORT",
                    documentName, "导入失败: 文件读取异常", ip);
            throw new BusinessException(400, "文件读取失败，请确认文件为 UTF-8 编码");
        }

        // Clean text
        content = cleanText(content);

        // Split by "第X条"
        List<String[]> segments = splitByArticle(content);

        if (segments.isEmpty()) {
            operationLogService.logFailure(userId, username, "LEGAL_TXT_IMPORT",
                    documentName, "导入失败: 未识别到任何'第X条'条款标记", ip);
            throw new BusinessException(422, "未在文件中识别到任何'第X条'条款标记，请确认文件格式");
        }

        // Create document
        LegalDocument doc = new LegalDocument();
        doc.setDocumentName(documentName);
        doc.setDomain(domain);
        doc.setDocumentType(documentType);
        doc.setEffectiveDate(parseDate(effectiveDate));
        doc.setSource(source != null ? source : "");
        doc.setValidStatus(1);
        doc.setArticleCount(segments.size());
        doc.setVectorStatus("NONE");
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        documentMapper.insert(doc);

        // Create articles
        for (String[] seg : segments) {
            LegalArticle article = new LegalArticle();
            article.setDocumentId(doc.getId());
            article.setArticleNumber(seg[0]);
            article.setContent(seg[1]);
            article.setDomain(doc.getDomain());
            article.setSource(doc.getSource());
            article.setValidStatus(1);
            article.setVectorStatus("NONE");
            article.setCreatedAt(LocalDateTime.now());
            article.setUpdatedAt(LocalDateTime.now());
            articleMapper.insert(article);
        }

        operationLogService.log(userId, username, "LEGAL_TXT_IMPORT",
                doc.getDocumentName(), "导入完成，切分" + segments.size() + "条", ip);

        return TxtImportResult.success(doc.getId(), doc.getDocumentName(), segments.size());
    }

    // ---- private helpers ----

    private String cleanText(String text) {
        // Remove BOM
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        // Full-width spaces to half-width
        text = text.replace('　', ' ');
        // Collapse multiple spaces (not newlines) to single space
        text = text.replaceAll("[ ]{2,}", " ");
        // Collapse runs of blank lines (3+ newlines → 2 newlines)
        text = text.replaceAll("\\n{3,}", "\n\n");
        // Trim leading/trailing whitespace on each line
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\\n")) {
            sb.append(line.trim()).append("\n");
        }
        // Remove non-printable control chars except \n and \t
        text = sb.toString().replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
        return text.trim();
    }

    private List<String[]> splitByArticle(String text) {
        List<String[]> result = new ArrayList<>();
        // Prepend \n so that article headers at position 0 are matched
        String fullText = "\n" + text;
        Matcher matcher = ARTICLE_PATTERN.matcher(fullText);

        List<Integer> matchStarts = new ArrayList<>();
        List<Integer> matchEnds = new ArrayList<>();
        List<String> numbers = new ArrayList<>();

        while (matcher.find()) {
            matchStarts.add(matcher.start());
            matchEnds.add(matcher.end());
            numbers.add(matcher.group(1));
        }

        if (numbers.isEmpty()) {
            return result;
        }

        for (int i = 0; i < numbers.size(); i++) {
            // Content starts right after the matched header (including \s* consumed)
            int contentStart = matchEnds.get(i);
            // Content ends at the start of the next header, or end of text
            int contentEnd = (i + 1 < matchStarts.size()) ? matchStarts.get(i + 1) : fullText.length();

            String articleContent = fullText.substring(contentStart, contentEnd).trim();
            // Clean up excessive blank lines within content
            articleContent = articleContent.replaceAll("\\n{3,}", "\n\n").trim();

            if (!articleContent.isEmpty()) {
                result.add(new String[]{numbers.get(i), articleContent});
            }
        }

        return result;
    }

    private void recalcArticleCount(Long documentId) {
        LambdaQueryWrapper<LegalArticle> q = new LambdaQueryWrapper<>();
        q.eq(LegalArticle::getDocumentId, documentId);
        long count = articleMapper.selectCount(q);
        LegalDocument doc = documentMapper.selectById(documentId);
        if (doc != null) {
            doc.setArticleCount((int) count);
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(doc);
        }
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            return null;
        }
    }
}
