package com.datamountaineer.streamreactor.connect.azure.documentdb.sink

import com.datamountaineer.streamreactor.connect.azure.documentdb.config.DocumentDbConfig
import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.azure.documentdb._
import org.apache.kafka.connect.sink.SinkRecord
import org.mockito.ArgumentMatchers.{any, eq => mockEq}
import org.mockito.Mockito.{verify, _}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}

import scala.collection.JavaConversions._

class DocumentDbSinkTaskMapTest extends WordSpec with Matchers with MockitoSugar with MatchingArgument {
  private val connection = "https://accountName.documents.azure.com:443/"

  val mapper = new ObjectMapper()
  val `type` = mapper.getTypeFactory.constructMapType(classOf[java.util.HashMap[String, Any]], classOf[String], classOf[Any])

  "DocumentDbSinkTask" should {
    "handle util.Map INSERTS with default consistency level" in {
      val map = Map(
        DocumentDbConfig.DATABASE_CONFIG -> "database1",
        DocumentDbConfig.CONNECTION_CONFIG -> connection,
        DocumentDbConfig.MASTER_KEY_CONFIG -> "secret",
        DocumentDbConfig.KCQL_CONFIG -> "INSERT INTO coll1 SELECT * FROM topic1;INSERT INTO coll2 SELECT * FROM topic2"
      )

      val documentClient = mock[DocumentClient]
      val dbResource: ResourceResponse[Database] = mock[ResourceResponse[Database]]
      when(dbResource.getResource).thenReturn(mock[Database])

      Seq("dbs/database1/colls/coll1",
        "dbs/database1/colls/coll2").foreach { c =>
        val resource = mock[ResourceResponse[DocumentCollection]]
        when(resource.getResource).thenReturn(mock[DocumentCollection])

        when(documentClient.readCollection(mockEq(c), any(classOf[RequestOptions])))
          .thenReturn(resource)

        when(documentClient.readCollection(mockEq(c), mockEq(null)))
          .thenReturn(resource)
      }

      when(documentClient.readDatabase(mockEq("dbs/database1"), mockEq(null)))
        .thenReturn(dbResource)

      val task = new DocumentDbSinkTask(s => documentClient)
      task.start(map)

      val json1 = scala.io.Source.fromFile(getClass.getResource(s"/transaction1.json").toURI.getPath).mkString
      val map1: java.util.Map[String, Any] = mapper.readValue(json1, `type`)

      val json2 = scala.io.Source.fromFile(getClass.getResource(s"/transaction2.json").toURI.getPath).mkString
      val map2: java.util.Map[String, Any] = mapper.readValue(json2, `type`)

      val sinkRecord1 = new SinkRecord("topic1", 0, null, null, null, map1, 1000)
      val sinkRecord2 = new SinkRecord("topic2", 0, null, null, null, map2, 1000)

      val doc1 = new Document(json1)
      val r1 = mock[ResourceResponse[Document]]
      when(r1.getResource).thenReturn(doc1)

      when(
        documentClient
          .createDocument(
            mockEq("dbs/database1/colls/coll1"),
            argThat { argument: Document =>
              argument != null && argument.toString == doc1.toString
            }, argThat { argument: RequestOptions =>
              argument.getConsistencyLevel == ConsistencyLevel.Session
            }, mockEq(false)))
        .thenReturn(r1)

      val doc2 = new Document(json2)
      val r2 = mock[ResourceResponse[Document]]
      when(r2.getResource).thenReturn(doc2)

      when(
        documentClient
          .createDocument(
            mockEq("dbs/database1/colls/coll2"),
            argThat { argument: Document =>
              argument != null && argument.toString == doc2.toString
            },
            argThat { argument: RequestOptions => argument.getConsistencyLevel == ConsistencyLevel.Session
            }, mockEq(false)))
        .thenReturn(r2)

      task.put(Seq(sinkRecord1, sinkRecord2))

      verify(documentClient)
        .createDocument(
          mockEq("dbs/database1/colls/coll1"),
          argThat { argument: Document =>
            argument.toString == doc1.toString
          },
          argThat { argument: RequestOptions =>
            argument.getConsistencyLevel == ConsistencyLevel.Session
          }, mockEq(false))

      verify(documentClient)
        .createDocument(
          mockEq("dbs/database1/colls/coll2"),
          argThat { argument: Document =>
            doc2.toString == argument.toString
          },
          argThat { argument: RequestOptions =>
            argument.getConsistencyLevel == ConsistencyLevel.Session
          }
          , mockEq(false))
    }

    "handle util.Map INSERTS with Eventual consistency level" in {
      val map = Map(
        DocumentDbConfig.DATABASE_CONFIG -> "database1",
        DocumentDbConfig.CONNECTION_CONFIG -> connection,
        DocumentDbConfig.MASTER_KEY_CONFIG -> "secret",
        DocumentDbConfig.CONSISTENCY_CONFIG -> ConsistencyLevel.Eventual.toString,
        DocumentDbConfig.KCQL_CONFIG -> "INSERT INTO coll1 SELECT * FROM topic1;INSERT INTO coll2 SELECT * FROM topic2"
      )

      val documentClient = mock[DocumentClient]
      val dbResource: ResourceResponse[Database] = mock[ResourceResponse[Database]]
      when(dbResource.getResource).thenReturn(mock[Database])

      Seq("dbs/database1/colls/coll1",
        "dbs/database1/colls/coll2").foreach { c =>
        val resource = mock[ResourceResponse[DocumentCollection]]
        when(resource.getResource).thenReturn(mock[DocumentCollection])

        when(documentClient.readCollection(mockEq(c), any(classOf[RequestOptions])))
          .thenReturn(resource)

        when(documentClient.readCollection(mockEq(c), mockEq(null)))
          .thenReturn(resource)
      }

      when(documentClient.readDatabase(mockEq("dbs/database1"), mockEq(null)))
        .thenReturn(dbResource)

      val task = new DocumentDbSinkTask(s => documentClient)
      task.start(map)

      val json1 = scala.io.Source.fromFile(getClass.getResource(s"/transaction1.json").toURI.getPath).mkString
      val map1: java.util.Map[String, Any] = mapper.readValue(json1, `type`)

      val json2 = scala.io.Source.fromFile(getClass.getResource(s"/transaction2.json").toURI.getPath).mkString
      val map2: java.util.Map[String, Any] = mapper.readValue(json2, `type`)

      val sinkRecord1 = new SinkRecord("topic1", 0, null, null, null, map1, 1000)
      val sinkRecord2 = new SinkRecord("topic2", 0, null, null, null, map2, 1000)

      val doc1 = new Document(json1)
      val r1 = mock[ResourceResponse[Document]]
      when(r1.getResource).thenReturn(doc1)

      when(
        documentClient
          .createDocument(
            mockEq("dbs/database1/colls/coll1"),
            argThat { argument: Document =>
              argument != null && argument.toString == doc1.toString
            },
            argThat { argument: RequestOptions =>
              argument.getConsistencyLevel == ConsistencyLevel.Eventual
            },
            mockEq(false)))
        .thenReturn(r1)

      val doc2 = new Document(json2)
      val r2 = mock[ResourceResponse[Document]]
      when(r2.getResource).thenReturn(doc2)

      when(
        documentClient
          .createDocument(
            mockEq("dbs/database1/colls/coll2"),
            argThat { argument: Document =>
              argument != null && argument.toString == doc2.toString
            },
            argThat { argument: RequestOptions =>
              argument.getConsistencyLevel == ConsistencyLevel.Eventual
            },
            mockEq(false)))
        .thenReturn(r2)

      task.put(Seq(sinkRecord1, sinkRecord2))

      verify(documentClient)
        .createDocument(
          mockEq("dbs/database1/colls/coll1"),
          argThat { argument: Document =>
            argument.toString == doc1.toString
          },
          argThat { argument: RequestOptions =>
            argument.getConsistencyLevel == ConsistencyLevel.Eventual
          },
          mockEq(false))

      verify(documentClient)
        .createDocument(
          mockEq("dbs/database1/colls/coll2"),
          argThat { argument: Document =>
            doc2.toString == argument.toString
          },
          argThat { argument: RequestOptions =>
            argument.getConsistencyLevel == ConsistencyLevel.Eventual
          },
          mockEq(false))
    }

    "handle util.Map UPSERT with Eventual consistency level" in {
      val map = Map(
        DocumentDbConfig.DATABASE_CONFIG -> "database1",
        DocumentDbConfig.CONNECTION_CONFIG -> connection,
        DocumentDbConfig.MASTER_KEY_CONFIG -> "secret",
        DocumentDbConfig.CONSISTENCY_CONFIG -> ConsistencyLevel.Eventual.toString,
        DocumentDbConfig.KCQL_CONFIG -> "UPSERT INTO coll1 SELECT * FROM topic1 PK time"
      )

      val documentClient = mock[DocumentClient]
      val dbResource: ResourceResponse[Database] = mock[ResourceResponse[Database]]
      when(dbResource.getResource).thenReturn(mock[Database])


      val resource = mock[ResourceResponse[DocumentCollection]]
      when(resource.getResource).thenReturn(mock[DocumentCollection])

      when(documentClient.readCollection(mockEq("dbs/database1/colls/coll1"), any(classOf[RequestOptions])))
        .thenReturn(resource)


      when(documentClient.readDatabase(mockEq("dbs/database1"), mockEq(null)))
        .thenReturn(dbResource)

      val task = new DocumentDbSinkTask(s => documentClient)
      task.start(map)

      val json1 = scala.io.Source.fromFile(getClass.getResource(s"/transaction1.json").toURI.getPath).mkString
      val map1: java.util.Map[String, Any] = mapper.readValue(json1, `type`)
      val json2 = scala.io.Source.fromFile(getClass.getResource(s"/transaction2.json").toURI.getPath).mkString
      val map2: java.util.Map[String, Any] = mapper.readValue(json2, `type`)
      val sinkRecord1 = new SinkRecord("topic1", 0, null, null, null, map1, 1000)
      val sinkRecord2 = new SinkRecord("topic1", 0, null, null, null, map2, 1000)

      val doc1 = new Document(json1)
      doc1.setId(doc1.get("time").toString)
      val r1 = mock[ResourceResponse[Document]]
      when(r1.getResource).thenReturn(doc1)

      when(
        documentClient
          .upsertDocument(
            mockEq("dbs/database1/colls/coll1"),
            argThat { argument: Document =>
              argument != null && argument.toString == doc1.toString
            },
            argThat { argument: RequestOptions =>
              argument.getConsistencyLevel == ConsistencyLevel.Eventual
            },
            mockEq(true)))
        .thenReturn(r1)

      val doc2 = new Document(json2)
      doc2.setId(doc2.get("time").toString)
      val r2 = mock[ResourceResponse[Document]]
      when(r2.getResource).thenReturn(doc2)

      when(
        documentClient
          .upsertDocument(
            mockEq("dbs/database1/colls/coll1"),
            argThat { argument: Document =>
              argument != null && argument.toString == doc2.toString
            },
            argThat { argument: RequestOptions =>
              argument.getConsistencyLevel == ConsistencyLevel.Eventual
            }, mockEq(true)))
        .thenReturn(r2)

      task.put(Seq(sinkRecord1, sinkRecord2))

      verify(documentClient)
        .upsertDocument(
          mockEq("dbs/database1/colls/coll1"),
          argThat { argument: Document =>
            argument.toString == doc1.toString
          },
          argThat { argument: RequestOptions =>
            argument.getConsistencyLevel == ConsistencyLevel.Eventual
          }, mockEq(true))

      verify(documentClient)
        .upsertDocument(
          mockEq("dbs/database1/colls/coll1"),
          argThat { argument: Document =>
            doc2.toString == argument.toString
          },
          argThat { argument: RequestOptions =>
            argument.getConsistencyLevel == ConsistencyLevel.Eventual
          },
          mockEq(true))
    }


  }
}

