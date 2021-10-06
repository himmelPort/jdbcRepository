## простой синтаксис вызова процедур и sql-выражений JDBC

Подключить библиотеку можно так:
```xml
<dependency>
    <groupId>mag.jdbc</groupId>
    <artifactId>repository</artifactId>
    <version>1.0</version>
    <scope>compile</scope>
</dependency>
```
Для её работы также необходимы библиотеки:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jdbc</artifactId>
</dependency>
```
Примеры использования:
```java
public List<Illustration> imageList(int[] bordering) {
    return jdbcRepository.procedure("imagelist")
    .param(bordering[0]).param(bordering[1])
    .jdbcSelectMap(IllustrationMapper::new);
    }
```
* здесь процедура imagelist возвращает список строк. Строки отображаются
  на объекты Illustration. Отображение строк на объекты класса Illustration выполняется классом IllustrationMapper.
  Параметры вызова процедуры устанавливаются с помощью метода param(T parameter). Здесь передаётся два параметра
  и метод вызывается дважды.
  Способ отображения устанавливается в методе jdbcSelectMap(IllustrationMapper::new)
  как Supplier<M> rowMapper.
Пример класса-отображателя:
```java
public class IllustrationMapper implements RowMapper<Illustration> {
    @Override
    public Illustration mapRow(ResultSet resultSet, int i) throws SQLException {
        final String idImage = resultSet.getString("idImage");
        ...
        final byte[] dataImage = resultSet.getBytes("dataImage");
        return new Illustration(idImage, ... dataImage);
    }
}
```
* процедура imageone возвращает одну строку, отображаемую на объект класса Illustration.
отображение выполняется тем же классом IllustrationMapper. Последний аргумент Illustration::new
предназначен для создания объекта с помощью конструктора без параметров при 
отсутствии записей с назначенным условием.
```java         
public Illustration illustration(String idImage) {
        return jdbcRepository.presentByInt("imageone",
        idImage, IllustrationMapper::new, Illustration::new);
        }
```
* Процедуру вставки новой записи можно оформить так:
```java
public Illustration imageInsert(Illustration illustration) {
    return jdbcRepository.procedure("imageinsert")
    .param(illustration.getNameImage())
    .param(illustration.getDescriptorImage())
    .param(illustration.getSizeImage())
    .param(illustration.getDataImage())
    .jdbcInsertPresent(IllustrationMapper::new);
    }
```
* Процедура модификации записи выглядит так:
```java         
public void imageModify(Illustration illustration) {
        jdbcRepository.procedure("imagemodify").paramInt(illustration.getIdImage())
        .param(illustration.getNameImage())
        .param(illustration.getDescriptorImage())
        .jdbcCallQuery();
        }
```
* И процедура удаления так:
```java
public void imageDelete(String idImage) {
        jdbcRepository.procedure("imagedelete").paramInt(idImage)
        .jdbcCallQuery();
        }

```
Здесь метод paramInt() преобразует тип String в ожидаемый базой данных тип int.
* В следующем примере метод paramStrDefault(contextFilter, "%%")
  устанавливает значение переменной contextFilter по умолчанию в случае
  contextFilter = null;
```java
public List<Translator> questionsNode(String baseFilter, String idParent, String contextFilter) {
    return jdbcRepository.procedure("questionsNode")
    .paramInt(baseFilter)
    .paramInt(idParent)
    .paramStrDefault(contextFilter, "%%")
    .jdbcSelectMap(QuestionMapper::new);
    }
```
### Особенности применения
* Библиотека тестировалась в БД postgres-13. В ней вызов процедур, возвращающих 
набор строк, выполняется операторам select. Как, например,
```sql
select * from imagelist(:param1, :param2);
```
В среде базы данных H2 для аналогичной процедуры, называемой UDF,
используется оператор call:
```sql
call imagelist(:param1, :param2);
```
Для учёта такой особенности используется свойсво nameProcedure, имеющее
getter и setter. Если установить для этих разных баз данных используемое 
значение оператора:
```java
// for postgres
    jdbcRepository.setNameCommand("select * from");

// for h2
    jdbcRepository.setNameCommand("call");
```
то вызов процедур БД postgres и БД H2 будет выглядить одинаково:
```java
    jdbcRepository.procedure("schema_table")
        .param(appSchemaTable)
        .jdbcSelect(SchemaTableMapper::new);
```
* Значение поля nameProcedure не используется при вызове следующих методов:
```java
public <T, M> List<T> jdbcNativeQuery(String sql, Supplier<M> rowMapper)
  and
public void jdbcNativeQuery(String sql)
```
Первый метод отображает строки на объекты с помощью аргумента Supplier<M> rowMapper.

Второй выполнят операции insert и update.
    
