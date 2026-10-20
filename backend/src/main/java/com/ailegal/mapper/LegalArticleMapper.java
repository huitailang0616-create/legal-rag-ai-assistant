package com.ailegal.mapper;

import com.ailegal.dto.LegalArticleDocument;
import com.ailegal.entity.LegalArticle;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LegalArticleMapper extends BaseMapper<LegalArticle> {

    /**
     * Authoritative MySQL JOIN: legal_article + legal_document, both valid.
     * Qdrant payload is never used as the business fact source.
     */
    @Select("<script>" +
            "SELECT a.id AS article_id, a.document_id AS document_id, a.article_number, a.content, " +
            "a.valid_status AS article_valid_status, " +
            "d.document_name, d.domain, d.document_type, d.source, d.valid_status AS document_valid_status " +
            "FROM legal_article a " +
            "JOIN legal_document d ON a.document_id = d.id " +
            "WHERE a.id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "AND a.valid_status = 1 AND d.valid_status = 1" +
            "</script>")
    List<LegalArticleDocument> selectValidArticlesWithDocuments(@Param("ids") List<Long> ids);

    /**
     * 参考法条解析：按 (document_name, article_number) 稳定标识解析为当前有效 legal_article.id。
     * 两侧 valid_status=1；调用方在 Java 内按二元组配对，重复/缺失由调用方判定
     * （REFERENCE_ARTICLE_AMBIGUOUS / REFERENCE_ARTICLE_NOT_FOUND）。
     */
    @Select("<script>" +
            "SELECT a.id AS article_id, d.document_name, a.article_number " +
            "FROM legal_article a " +
            "JOIN legal_document d ON a.document_id = d.id " +
            "WHERE a.valid_status = 1 AND d.valid_status = 1 " +
            "AND d.document_name IN " +
            "<foreach collection='documentNames' item='n' open='(' separator=',' close=')'>#{n}</foreach> " +
            "AND a.article_number IN " +
            "<foreach collection='articleNumbers' item='n' open='(' separator=',' close=')'>#{n}</foreach>" +
            "</script>")
    List<LegalArticleDocument> selectValidReferenceIds(@Param("documentNames") List<String> documentNames,
                                                       @Param("articleNumbers") List<String> articleNumbers);
}
