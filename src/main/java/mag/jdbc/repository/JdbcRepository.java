/* project jdbcRepository
создано 28.08.2021 21:21
*/
package mag.jdbc.repository;

import org.springframework.context.annotation.Scope;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

@Repository
@Scope(value = "prototype")
public class JdbcRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final Map<String, Object> paramsPrepare = new HashMap<>();
    private final Map<String, Object> paramsQuery = new HashMap<>();
    private String executeMode;
    private String nameProcedure;
    private String nameCommand;
    private boolean useTransactional;

    public JdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.nameCommand = "select * from";
        clear();
    }
    private void clear() {
        paramsPrepare.clear();
        executeMode = "";
    }

    public String getNameCommand() {
        return nameCommand;
    }

    public void setNameCommand(String nameCommand) {
        this.nameCommand = nameCommand;
    }

    //  SINGLE PRESENT START
//  for nullStamp must be constructor without args
     @Transactional(readOnly = true)
    private <T, R, K> R singlePresentBase(String nameProcedure,
                                          Function<String, K> func, String identifyKey,
                                          Supplier<T> rowMapper, Supplier<R> nullStamp) {
        String sql = "select * from "  + nameProcedure + "(:identifyKey);";
        Map<String, Object> paramsQuery = new HashMap<>();
        paramsQuery.put("identifyKey", func.apply(identifyKey));
        List<T> list = jdbc.query(sql, paramsQuery, (RowMapper<T>) rowMapper.get());
        if (list.isEmpty()) return nullStamp.get();
        return (R) list.get(0);
    }
    public <T, R> R presentByInt(String nameProcedure, String identifyKey,
                                 Supplier<T> rowMapper, Supplier<R> nullStamp) {
        Function<String, Integer> func = Integer::valueOf;
        return singlePresentBase(nameProcedure, func, identifyKey, rowMapper, nullStamp);
    }
    public <T, R> R presentByString(String nameProcedure, String identifyKey,
                                    Supplier<T> rowMapper, Supplier<R> nullStamp) {
        Function<String, String> func = (k) -> (k);
        return singlePresentBase(nameProcedure, func, identifyKey, rowMapper, nullStamp);
    }
    @Transactional(readOnly = true)
    public <T, R, S> R presentByInt(
            String nameProcedure, String identifyKey, RowMapper<T> rowMapper,
            Function<S, R> funcNullStamp, Supplier<R> nullStamp) {
        String sql = "select * from "  + nameProcedure + "(:identifyKey);";
        Map<String, Object> paramsQuery = new HashMap<>();
        paramsQuery.put("identifyKey", Integer.parseInt(identifyKey));
        List<T> list = jdbc.query(sql, paramsQuery, rowMapper);
        if (list.isEmpty())
            return funcNullStamp.apply((S) nullStamp.get());
        return (R) list.get(0);
    }
//  SINGLE PRESENT STOP

    private <T> void addParam(T param) {
        String key = "param" + paramsPrepare.size();
        paramsPrepare.put(key, param);
    }

    public JdbcRepository procedure(String nameProcedure) {
        this.nameProcedure = nameProcedure;
        return this;
    }
    public <T> JdbcRepository param(T param) {
        addParam(param);
        return this;
    }
    public JdbcRepository paramInt(String param) {
        addParam(Integer.parseInt(param));
        return this;
    }
    public JdbcRepository paramStrDefault(String param, String strDefault) {
        if (param.length() == 0) param = strDefault;
        addParam(param);
        return this;
    }

    public JdbcRepository executeMode(String executeMode) {
        this.executeMode = executeMode;
        return this;
    }
    public JdbcRepository noTransactional() {
        this.useTransactional = false;
        return this;
    }

    private StringBuilder makeParamsQuery() {
        int i;
        String key;
        StringBuilder params = new StringBuilder();

        TreeMap<String, Object> sorted = new TreeMap<>(paramsPrepare);
        paramsQuery.clear();
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            key = entry.getKey();
            params.append(":").append(key).append(", ");
            paramsQuery.put(key, paramsPrepare.get(key));
        }
        i = params.length();
        if (i > 0) params.delete(i - 2, i);
        return params;
    }

    private StringBuilder prepareQuery(String preTextSql) {
        StringBuilder sql = new StringBuilder();

        if (!executeMode.isEmpty()) nameProcedure = nameProcedure + "check";
        sql.append(preTextSql).append(nameProcedure).append("(");

        StringBuilder params = makeParamsQuery();

        if (!executeMode.isEmpty())
            params.append(", :check");

        sql.append(params).append(");");
        if (!executeMode.isEmpty())
            paramsQuery.put("check", Boolean.valueOf(executeMode));

        clear();
        return sql;
    }

    @Transactional(readOnly = true)
    public <T, M> List<T> jdbcSelect(Supplier<M> rowMapper) {
        StringBuilder sql = prepareQuery(nameCommand +  " ");
        return jdbc.query(sql.toString(), paramsQuery, (RowMapper<T>) rowMapper.get());
    }

    @Transactional(readOnly = true)
    public <T, M> List<T> jdbcSelectMap(Supplier<M> rowMapper) {
        StringBuilder sql = prepareQuery("select * from ");
        return jdbc.query(sql.toString(), paramsQuery, (RowMapper<T>) rowMapper.get());
    }

    @Transactional(readOnly = true)
    public <T, M> List<T> jdbcCallAsSelect(Supplier<M> rowMapper) {
        StringBuilder sql = prepareQuery("call ");
        return jdbc.query(sql.toString(), paramsQuery, (RowMapper<T>) rowMapper.get());
    }

    @Transactional(readOnly = true)
    public ArrayList<ArrayList<String>> jdbcArrayArrayListString(List<Function<SqlRowSet, String>> data) {
        ArrayList<ArrayList<String>> arrayLists = new ArrayList<>();
        ArrayList<String> record;
        StringBuilder sql = prepareQuery("select * from ");
        SqlRowSet rs = jdbc.queryForRowSet(sql.toString(), paramsQuery);
        while (rs.next()) {
            record = new ArrayList<>();
            for (Function<SqlRowSet, String> func : data) {
                record.add(func.apply(rs));
            }
            arrayLists.add(record);
        }
        return arrayLists;
    }

    //  need external transaction
    public <T> T jdbcResultQuery() {
        StringBuilder sql = prepareQuery("select * from ");
        if (useTransactional) return resultTransactionalOn(sql);
        return resultTransactionalOff(sql);
    }
    public <T> T jdbcResultQuery(Function<SqlRowSet, T> extractData) {
        StringBuilder sql = prepareQuery("select * from ");
        if (useTransactional) return resultTransactionalOn(sql, extractData);
        return resultTransactionalOff(sql, extractData);
    }

    public void jdbcCallQuery() {
        StringBuilder sql = prepareQuery("call ");
        if (useTransactional) callTransactionalOn(sql);
        else callTransactionalOff(sql);
    }

    @Modifying
    public <T, M> T jdbcInsertPresent(Supplier<M> rowMapper) {
        this.useTransactional = true;
        StringBuilder sql = prepareQuery("select * from ");
        List<T> list = jdbc.query(sql.toString(), paramsQuery, (RowMapper<T>) rowMapper.get());
        if (list.isEmpty()) return null;
        return list.get(0);
    }

//  MODIFY PROCEDURES START
//  RESULT QUERY START
    @Modifying
    private <T> T resultTransactionalOn(StringBuilder sql) {
        this.useTransactional = true;
        SqlRowSet rs = jdbc.queryForRowSet(sql.toString(), paramsQuery);
        rs.first();
        return (T) rs.getObject(1);
    }
    @Modifying
    private <T> T resultTransactionalOn(StringBuilder sql, Function<SqlRowSet, T> extractData) {
        this.useTransactional = true;
        SqlRowSet rs = jdbc.queryForRowSet(sql.toString(), paramsQuery);
        rs.first();
        return extractData.apply(rs);
    }
    private <T> T resultTransactionalOff(StringBuilder sql) {
        this.useTransactional = true;
        SqlRowSet rs = jdbc.queryForRowSet(sql.toString(), paramsQuery);
        rs.first();
        return (T) rs.getObject(1);
    }
    private <T> T resultTransactionalOff(StringBuilder sql, Function<SqlRowSet, T> extractData) {
        this.useTransactional = true;
        SqlRowSet rs = jdbc.queryForRowSet(sql.toString(), paramsQuery);
        rs.first();
        return extractData.apply(rs);
    }
//  RESULT QUERY STOP

    @Modifying
    private void callTransactionalOn(StringBuilder sql) {
        this.useTransactional = true;
        jdbc.update(sql.toString(), paramsQuery);
    }
    private void callTransactionalOff(StringBuilder sql) {
        this.useTransactional = true;
        jdbc.update(sql.toString(), paramsQuery);
    }
//  MODIFY PROCEDURES STOP

//  USE NATIVE JDBC START
    private String appendParamsString(String sql) {
        StringBuilder params = makeParamsQuery();
        if (params.length() > 0)
            sql = sql + "(" + params + ")";
        clear();
        return sql;
    }

    @Transactional(readOnly = true)
    public <T, M> List<T> jdbcNativeQuery(String sql, Supplier<M> rowMapper) {
        return jdbc.query(appendParamsString(sql), paramsQuery, (RowMapper<T>) rowMapper.get());
    }
    @Modifying
    public void jdbcNativeQuery(String sql) {
        jdbc.update(appendParamsString(sql), paramsQuery);
    }
//  USE NATIVE JDBC END
}
