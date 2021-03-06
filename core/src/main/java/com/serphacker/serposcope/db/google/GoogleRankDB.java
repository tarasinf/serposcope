/* 
 * Serposcope - SEO rank checker https://serposcope.serphacker.com/
 * 
 * Copyright (c) 2016 SERP Hacker
 * @author Pierre Nogues <support@serphacker.com>
 * @license https://opensource.org/licenses/MIT MIT License
 */
package com.serphacker.serposcope.db.google;

import com.google.inject.Singleton;
import com.mysema.commons.lang.CloseableIterator;
import com.querydsl.core.QueryFlag;
import com.querydsl.core.Tuple;
import com.querydsl.sql.SQLQuery;
import com.querydsl.sql.dml.SQLDeleteClause;
import com.querydsl.sql.dml.SQLInsertClause;
import com.querydsl.sql.dml.SQLMergeClause;
import com.serphacker.serposcope.db.AbstractDB;
import com.serphacker.serposcope.models.google.GoogleBest;
import com.serphacker.serposcope.models.google.GoogleRank;
import com.serphacker.serposcope.models.google.GoogleTarget;
import com.serphacker.serposcope.querybuilder.QGoogleRank;
import com.serphacker.serposcope.querybuilder.QGoogleRankBest;
import java.sql.Connection;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Singleton
public class GoogleRankDB extends AbstractDB {

    static QGoogleRank t_rank = QGoogleRank.googleRank;
    static QGoogleRankBest t_best = QGoogleRankBest.googleRankBest;

    public boolean insertBest(GoogleBest best){
        boolean inserted = false;
        try(Connection con = ds.getConnection()){
            inserted = new SQLMergeClause(con, dbTplConf, t_best)
                .set(t_best.groupId, best.getGroupId())
                .set(t_best.googleTargetId, best.getGoogleTargetId())
                .set(t_best.googleSearchId, best.getGoogleSearchId())
                .set(t_best.rank, best.getRank())
                .set(t_best.url, best.getUrl())
                .set(t_best.runDay, best.getRunDay() == null ? null : Timestamp.valueOf(best.getRunDay()))
                .execute() == 1;
        } catch(Exception ex){
            LOG.error("SQL error", ex);
        }
        return inserted;
    }
    
    public GoogleBest getBest(int groupId, int googleTargetId, int googleSearchId){
        GoogleBest best = null;
        
        try(Connection con = ds.getConnection()){
            Tuple tuple = new SQLQuery<Void>(con, dbTplConf)
                .select(t_best.all())
                .from(t_best)
                .where(t_best.groupId.eq(groupId))
                .where(t_best.googleTargetId.eq(googleTargetId))
                .where(t_best.googleSearchId.eq(googleSearchId))
                .fetchOne();
            
            if(tuple != null){
                best = new GoogleBest(
                    tuple.get(t_best.groupId),
                    tuple.get(t_best.googleTargetId),
                    tuple.get(t_best.googleSearchId),
                    tuple.get(t_best.rank),
                    tuple.get(t_best.runDay) != null ? tuple.get(t_best.runDay).toLocalDateTime() : null,
                    tuple.get(t_best.url)
                );
            } else {
                best = new GoogleBest(groupId, googleTargetId, googleSearchId, (short)GoogleRank.UNRANKED, null, null);
            }
        } catch(Exception ex){
            LOG.error("SQL error", ex);
        }
        
        return best;        
    }
    
    public boolean insert(GoogleRank rank) {
        if(dbTplConf.getTemplates().isNativeMerge()){
            return insertMerge(rank);
        } else {
            return insertOnDuplicateKey(rank);
        }
    }       
    
    public boolean insertMerge(GoogleRank rank) {
        boolean inserted = false;
        try(Connection con = ds.getConnection()){
            inserted = new SQLMergeClause(con, dbTplConf, t_rank)
                .set(t_rank.runId, rank.runId)
                .set(t_rank.groupId, rank.groupId)
                .set(t_rank.googleTargetId, rank.googleTargetId)
                .set(t_rank.googleSearchId, rank.googleSearchId)
                .set(t_rank.rank, rank.rank)
                .set(t_rank.previousRank, rank.previousRank)
                .set(t_rank.diff, rank.diff)
                .set(t_rank.url, rank.url)
                .execute() == 1;
        } catch(Exception ex){
            LOG.error("SQL error", ex);
        }
        return inserted;
    }    
    
    public boolean insertOnDuplicateKey(GoogleRank rank) {
        boolean inserted = false;
        try(Connection con = ds.getConnection()){
            inserted = new SQLInsertClause(con, dbTplConf, t_rank)
                .set(t_rank.runId, rank.runId)
                .set(t_rank.groupId, rank.groupId)
                .set(t_rank.googleTargetId, rank.googleTargetId)
                .set(t_rank.googleSearchId, rank.googleSearchId)
                .set(t_rank.rank, rank.rank)
                .set(t_rank.previousRank, rank.previousRank)
                .set(t_rank.diff, rank.diff)
                .set(t_rank.url, rank.url)
                .addFlag(QueryFlag.Position.END, 
                    " on duplicate key update rank = " + rank.rank + 
                    ", previous_rank = " + rank.previousRank + 
                    ", diff = " + rank.diff +
                    ", url = " + (rank.url == null ? "NULL" : dbTplConf.asLiteral(rank.url))
                )
                .execute() == 1;
        } catch(Exception ex){
            LOG.error("SQL error", ex);
        }
        return inserted;
    }        
    
    public int get(int runId, int groupId, int googleTargetId, int googleSearchId){
        Short rank = null;
        
        try(Connection con = ds.getConnection()){
            
            rank = new SQLQuery<Void>(con, dbTplConf)
                .select(t_rank.rank)
                .from(t_rank)
                .where(t_rank.runId.eq(runId))
                .where(t_rank.groupId.eq(groupId))
                .where(t_rank.googleTargetId.eq(googleTargetId))
                .where(t_rank.googleSearchId.eq(googleSearchId))
                .fetchOne();
            
        } catch(Exception ex){
            LOG.error("SQL error", ex);
        }
        
        return rank != null ? rank : GoogleRank.UNRANKED;
    }

    public GoogleRank getFull(int runId, int groupId, int googleTargetId, int googleSearchId){
        GoogleRank rank = null;
        
        try(Connection con = ds.getConnection()){
            
            Tuple tuple = new SQLQuery<Void>(con, dbTplConf)
                .select(t_rank.all())
                .from(t_rank)
                .where(t_rank.runId.eq(runId))
                .where(t_rank.groupId.eq(groupId))
                .where(t_rank.googleTargetId.eq(googleTargetId))
                .where(t_rank.googleSearchId.eq(googleSearchId))
                .fetchOne();
            
            rank = fromTuple(tuple);
            
        } catch(Exception ex){
            LOG.error("SQL error", ex);
        }
        
        return rank;
    }    
    
    public List<GoogleRank> list(int runId, int groupId, int targetId){
        List<GoogleRank> ranks = new ArrayList<>();
        
        try(Connection con = ds.getConnection()){
            
            List<Tuple> tuples = new SQLQuery<Void>(con, dbTplConf)
                .select(t_rank.all())
                .from(t_rank)
                .where(t_rank.runId.eq(runId))
                .where(t_rank.groupId.eq(groupId))
                .where(t_rank.googleTargetId.eq(targetId))
                .fetch();
            
            for (Tuple tuple : tuples) {
                ranks.add(fromTuple(tuple));
            }
            
        } catch(Exception ex){
            LOG.error("SQL error", ex);
        }
        
        return ranks;
    }        
    
    public boolean deleteBySearch(int groupId, int googleSearchId){
        boolean deleted = false;
        
        try(Connection con = ds.getConnection()){
            
            deleted = new SQLDeleteClause(con, dbTplConf, t_best)
                .where(t_best.groupId.eq(groupId))
                .where(t_best.googleSearchId.eq(googleSearchId))
                .execute() > 0;             
            
            deleted &= new SQLDeleteClause(con, dbTplConf, t_rank)
                .where(t_rank.groupId.eq(groupId))
                .where(t_rank.googleSearchId.eq(googleSearchId))
                .execute() > 0;
            
        } catch(Exception ex){
            LOG.error("SQL error", ex);
        }        
        
        return deleted;
    }
    
    public boolean deleteByTarget(int groupId, int googleTargetId){
        boolean deleted = false;
        
        try(Connection con = ds.getConnection()){
            
            deleted = new SQLDeleteClause(con, dbTplConf, t_best)
                .where(t_best.groupId.eq(groupId))
                .where(t_best.googleTargetId.eq(googleTargetId))
                .execute() > 0;            
            
            deleted &= new SQLDeleteClause(con, dbTplConf, t_rank)
                .where(t_rank.groupId.eq(groupId))
                .where(t_rank.googleTargetId.eq(googleTargetId))
                .execute() > 0;
            
        } catch(Exception ex){
            LOG.error("SQL error", ex);
        }        
        
        return deleted;
    }    
    
    public void deleteByRunId(Integer runId){
        try(Connection con = ds.getConnection()){
            new SQLDeleteClause(con, dbTplConf, t_rank)
                .where(t_rank.runId.eq(runId))
                .execute();
        } catch(Exception ex){
            LOG.error("SQL error", ex);
        }
    }        
    
    public void wipe(){
        try(Connection con = ds.getConnection()){
            new SQLDeleteClause(con, dbTplConf, t_best).execute();
            new SQLDeleteClause(con, dbTplConf, t_rank).execute();
        } catch(Exception ex){
            LOG.error("SQL error", ex);
        }
    }    
    
    public static GoogleRank fromTuple(Tuple tuple){
        if(tuple == null){
            return null;
        }
        
        return new GoogleRank(
            tuple.get(t_rank.runId),
            tuple.get(t_rank.groupId),
            tuple.get(t_rank.googleTargetId),
            tuple.get(t_rank.googleSearchId),
            tuple.get(t_rank.rank),
            tuple.get(t_rank.previousRank),
            tuple.get(t_rank.url)
        );
    }
    
}
