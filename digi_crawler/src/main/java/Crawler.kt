import kotlin.collections.*
import kotlin.concurrent.thread
import org.jsoup.Jsoup
import org.neo4j.driver.v1.*
import org.neo4j.driver.v1.Values.parameters
import org.neo4j.driver.v1.exceptions.ServiceUnavailableException
import java.util.concurrent.atomic.AtomicInteger
import us.codecraft.xsoup.Xsoup
import java.io.File
import java.util.regex.Pattern
import java.util.HashMap


private class Crawler {
    private var addressList = mutableListOf<String>()
    private var startedJobs = AtomicInteger(0)
    private var pointer = AtomicInteger(1)
    private val maxDepth = 500_000
    private val basePath = System.getProperty("user.dir")
    private val sessionCounter = "$basePath\\x.sav"
    private var stopped = false
    private var exited = false
    var startFrom = 0
    val neo4jInit = Neo4jDriver("bolt://localhost:7687", "neo4j", "qazWSXEDCRFV!1")

    init {
        System.setProperty("javax.net.ssl.trustStore", "$basePath\\digi2.jks")
        startFrom = try {
            val xz = sessionCounter
            val data = File(xz).readText()
            data.toInt()
        } catch (e: Exception) {
            0
        }
        for (i in startFrom..maxDepth) {
            addressList.add("http://www.digikala.com/Product/DKP-$i")
        }

    }

    fun stop() {
        stopped = true
        while (!exited) {
            Thread.sleep(2000)
        }
        neo4jInit.close()
    }

    fun addressCount(): Int {
        return addressList.size
    }

    fun currentCount(): Int {
        return pointer.get()
    }

    private fun loadAddress(addressToGet: String): PageData? {

        val jx = Jsoup.connect(addressToGet).validateTLSCertificates(false).get()
        val x = Xsoup.compile("//*[@id=\"frmSecProductMain\"]/div[2]/header/div[1]/h1/text()").evaluate(jx).get()
        val price = Xsoup.compile("//*[@id=\"notifyMeButton\"]/@data-price").evaluate(jx).get()
        if (price.toLong() < 0) {
            return null
        }
        val enTitle = Xsoup.compile("//*[@id=\"frmSecProductMain\"]/div[2]/header/div[1]/h1/span/text()").evaluate(jx).get()
        val brand = Xsoup.compile("//*[@id=\"frmSecProductMain\"]/div[2]/header/div/div/div[1]/a/text()").evaluate(jx).get()
        val image = Xsoup.compile("//*[@id=\"frmSecProductMain\"]/div[1]/div[2]/div/img/@src").evaluate(jx).get()
        //val description = Xsoup.compile("//*[@id=\"frmSecProductDescription\"]/div/div/p[2]/text()").evaluate(jx).get()
        val description = Xsoup.compile("//*[@id=\"frmSecProductDescription\"]/div/div/p/text()").evaluate(jx).get()

        val pd = PageData()
        pd.title = x
        pd.enTitle = enTitle
        pd.brand = brand
        pd.description = description
        pd.imageData = image
        pd.price = price
        return pd

    }

    fun startCrawl() {

        neo4jInit.resetCounter()
        while (pointer.get() < addressCount() && !stopped) {
            if (pointer.get() % 10 == 0) {
                while (startedJobs.get() > 0) {
                    Thread.sleep(5_000)
                }
                File(sessionCounter).writeText("${pointer.get()}")
            }
            val addressPointer = addressList[pointer.get()]
            thread {
                try {

                    startedJobs.addAndGet(1)
                    val res = loadAddress(addressPointer)
                    neo4jInit.addCounter()
                    if(res != null){
                    val product = Product()
                    product.FaTitle = res.title
                    product.EnTitle = res.enTitle
                    product.Price = res.price
                    product.ImagePath = res.imageData
                    product.Description = res.description
                    product.SourceURL = addressPointer
                    neo4jInit.addCounter()
                        // neo4jInit.addProduct(product)
                        println("1-Current Pointer : ${pointer.get()} and Address : $addressPointer >>> ${product.EnTitle}")
                    }

                } catch (e: Exception) {
                    println(e.message)
                } finally {
                    startedJobs.addAndGet(-1)
                }

            }
            pointer.addAndGet(1)
        }
        exited = true
    }
}

class Neo4jDriver(uri: String, user: String, password: String) : AutoCloseable {
    private var driver: Driver

    init {
        driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password))
    }

    override fun close() {
        driver.close()
    }

    fun addProduct(product: Product): Boolean {
        try {
            driver.session().use({ session ->
                return session.writeTransaction(object : TransactionWork<Boolean> {
                    override fun execute(tx: Transaction): Boolean {
                        val query = "CALL dor.createProduct('${product.FaTitle}','${product.EnTitle}','${product.Description}',${product.Price},'${product.SourceURL}','${product.ImagePath}')"
                        tx.run(query)
                        return true
                    }
                })
            })
        } catch (ex: ServiceUnavailableException) {
            return false
        }
    }

    fun resetCounter(): Unit {
        try {
            driver.session().use({ session ->
                return session.writeTransaction(object : TransactionWork<Unit> {
                    override fun execute(tx: Transaction): Unit {
                        val query = """
                            MATCH (rs:RS)
                            WHERE rs.SiteId = "50cfc9e8-402b-495b-8ed4-66dcb2b3aadd"
                            SET rs.CountedProduct = 0""".trimMargin();
                        tx.run(query)

                    }
                })
            })
        } catch (ex: ServiceUnavailableException) {

        }
    }

    fun addCounter(): Unit {
        try {
            driver.session().use({ session ->
                return session.writeTransaction(object : TransactionWork<Unit> {
                    override fun execute(tx: Transaction): Unit {
                        val updateCounter = """
                                    MATCH (rs:RS)
                                    WHERE rs.SiteId = "50cfc9e8-402b-495b-8ed4-66dcb2b3aadd"
                                    WITH rs.CountedProduct as count, rs
                                    SET rs.CountedProduct = count + 1""".trimMargin();
                        tx.run(updateCounter)

                    }
                })
            })
        } catch (ex: ServiceUnavailableException) {

        }


    }
}

fun main(args: Array<String>) {
    val x = Crawler()
    println("Welcome to DX Crawler!")
    while (true) {
        print("Tell : ")
        val c = readLine()
        if (c == "stop") {
            x.stop()
            println("Bye Bye")
            break
        } else if (c == "state") {
            println("Current State : ${x.currentCount()}")
        } else if (c == "start") {
            thread {
                x.startCrawl()
            }
        }
    }
}
