import kotlin.collections.*
import kotlin.concurrent.thread
import org.jsoup.Jsoup
import java.util.concurrent.atomic.AtomicInteger
import us.codecraft.xsoup.Xsoup
import java.io.File

private class Crawler{
    private var addressList = listOf<String>().toMutableList()
    private var startedJobs = AtomicInteger(0)
    private var pointer = AtomicInteger(1)
    private val maxDepth = 500_000
    private val basePath = System.getProperty("user.dir")
    private val sessionCounter = "$basePath\\x.sav"
    private var stopped = false
    private var exited = false
    var startFrom = 0
    init{
        System.setProperty("javax.net.ssl.trustStore", "$basePath\\digi2.jks")
        startFrom = try{
            val xz = sessionCounter
            val data = File(xz).readText()
            data.toInt()
        }
        catch (e: Exception){
            0
        }
        for (i in startFrom..maxDepth){
            addressList.add("http://www.digikala.com/Product/DKP-$i")
        }
    }
    fun stop(){
        stopped = true
        while(!exited){
            Thread.sleep(2000)
        }
    }
    fun addressCount():Int{
        return addressList.size
    }
    fun currentCount(): Int{
        return pointer.get()
    }
    private fun loadAddress(addressToGet:String):PageData{
        val jx = Jsoup.connect(addressToGet).get()
        val x = Xsoup.compile("//*[@id=\"frmSecProductMain\"]/div[2]/header/div[1]/h1/text()").evaluate(jx).get()
        val enTitle = Xsoup.compile("//*[@id=\"frmSecProductMain\"]/div[2]/header/div[1]/h1/span/text()").evaluate(jx).get()
        val price = Xsoup.compile("//*[@id=\"notifyMeButton\"]/@data-price").evaluate(jx).get()
        val brand = Xsoup.compile("//*[@id=\"frmSecProductMain\"]/div[2]/header/div/div/div[1]/a/text()").evaluate(jx).get()
        val image = Xsoup.compile("//*[@id=\"frmSecProductMain\"]/div[1]/div[2]/div/img/@src").evaluate(jx).get()
        val description = Xsoup.compile("//*[@id=\"frmSecProductDescription\"]/div/div/p[2]/text()").evaluate(jx).get()
        val pd = PageData()
        pd.title = x
        pd.enTitle = enTitle
        pd.brand = brand
        pd.description = description
        pd.imageData = image
        pd.price = price
        return pd
    }

    fun startCrawl(){
        while(pointer.get() < addressCount() && !stopped){
            if(pointer.get() % 10 == 0){
                while (startedJobs.get() > 0){
                    Thread.sleep(5_000)
                }
                File(sessionCounter).writeText("${pointer.get()}")
            }
            val addressPointer = addressList[pointer.get()]
            thread {
                try {
                    startedJobs.addAndGet(1)
                    val res = loadAddress(addressPointer)

                    println("Current Pointer : ${pointer.get()} and Address : $addressPointer >>> ${res.title}")
                }
                catch (e: Exception) {
                    println(e.message)
                }
                finally {
                    startedJobs.addAndGet(-1)
                }

            }
            pointer.addAndGet(1)
        }
        exited = true
    }
}
fun main(args: Array<String>){
    val x = Crawler()
    println("Welcome to DX Crawler!")
    while(true){
        print("Tell : ")
        val c = readLine()
        if(c=="stop"){
            x.stop()
            println("Bye Bye")
            break
        }
        else if(c=="state"){
            println("Current State : ${x.currentCount()}")
        }
        else if(c=="start"){
            thread{
                x.startCrawl()
            }
        }
    }
}