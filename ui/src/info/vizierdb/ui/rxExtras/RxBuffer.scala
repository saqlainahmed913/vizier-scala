package info.vizierdb.ui.rxExtras

import scala.collection.mutable

abstract class RxBuffer[A]
  extends Seq[A]
{
  val elements = mutable.Buffer[A]()
  def apply(idx: Int) = elements(idx)
  def iterator = elements.iterator
  def length = { println("Length"); elements.length }

  def deliverUpdatesTo[T2 <: RxBufferWatcher[A]](handler: T2): T2
  def rxMap[B](f: A => B): DerivedRxBuffer[A, B] =
  { 
    val ret = new DerivedRxBuffer(f)
    ret.onInsertAll(0, elements)
    deliverUpdatesTo(ret)
    return ret
  }
}
object RxBuffer
{
  def apply[A](initial: A*): RxBufferVar[A] = 
  {
    val ret = new RxBufferVar[A]()
    ret.appendAll(initial)
    return ret
  }

  var idCounter = 0
}

abstract class RxBufferBase[A,B]
  extends RxBuffer[B]
{
  var id = { RxBuffer.idCounter += 1; RxBuffer.idCounter }
  private val watchers = mutable.Buffer[RxBufferWatcher[B]]()

  val derive: A => B

  def onAppend(sourceElem: A): Unit =
  {
    println(s"$this@$id += $sourceElem (${watchers.size} watchers)")
    doAppend(derive(sourceElem))
  }

  def doAppend(targetElem: B): Unit =
  {
    println(s"Apply $this@$id += $targetElem (${watchers.size} watchers)")
    elements += targetElem
    watchers.foreach { x => println(s"Notifying: $x"); x.onAppend(targetElem) }
  }

  def onPrepend(sourceElem: A): Unit =
  {
    println(s"$sourceElem +=: $this@$id (${watchers.size} watchers)")
    doPrepend(derive(sourceElem))
  }

  def doPrepend(targetElem: B): Unit =
  {
    println(s"Apply $targetElem +=: $this@$id (${watchers.size} watchers)")
    targetElem +=: elements
    watchers.foreach { _.onPrepend(targetElem) }
  }

  def onClear(): Unit = 
  {
    elements.clear()
    watchers.foreach { _.onClear() }
  }

  def onInsertAll(n: Int, sourceElems: collection.Traversable[A]) =
  {
    println(s"$this@$id << $sourceElems (${watchers.size} watchers)")
    doInsertAll(n, sourceElems.map { derive(_) })
  }

  def doInsertAll(n: Int, targetElems: collection.Traversable[B]): Unit =
  {
    println(s"Apply $this@$id << $targetElems (${watchers.size} watchers)")
    elements.insertAll(n, targetElems)
    watchers.foreach { _.onInsertAll(n, targetElems) }
  }

  def onRemove(n: Int): Unit =
  {
    watchers.foreach { _.onRemove(n) }
    elements.remove(n)
  }

  def onUpdate(n: Int, sourceElem: A): Unit =
  {
    doUpdate(n, derive(sourceElem))
  }

  def doUpdate(n: Int, targetElem: B): Unit =
  {
    elements.update(n, targetElem)
    watchers.foreach { _.onUpdate(n, targetElem) }
  }

  def deliverUpdatesTo[T2 <: RxBufferWatcher[B]](handler: T2): T2 =
  {
    watchers += handler
    println(s"Registered watcher on $this@$id (now ${watchers.size} watchers)")
    return handler
  }
}

trait RxBufferWatcher[A]
{
  def onAppend(elem: A): Unit
  def onPrepend(elem: A): Unit
  def onClear(): Unit
  def onInsertAll(n: Int, elems: collection.Traversable[A]): Unit
  def onRemove(n: Int): Unit
  def onUpdate(n: Int, elem: A): Unit
}

trait SimpleRxBufferWatcher[A]
  extends RxBufferWatcher[A]
{
  def onInsert(n: Int, elem: A)

  def onInsertAll(n: Int, elems: collection.Traversable[A]) =
    elems.toSeq
         .zipWithIndex
         .foreach { x => onInsert(n+x._2, x._1) }

}

class RxBufferVar[A]
  extends RxBufferBase[A,A]
  with mutable.Buffer[A]
{
  val derive = (a: A) => a

  def +=(elem: A) = 
    { onAppend(elem); this }
  
  def +=:(elem: A) = 
    { onPrepend(elem); this }

  def clear() =
    onClear()

  def insertAll(n: Int, elems: collection.Traversable[A]) =
    onInsertAll(n, elems)

  def remove(n: Int) = 
    { val v = apply(n); onRemove(n); v }

  def update(n: Int, elem: A) = 
    onUpdate(n, elem)
}

class DerivedRxBuffer[A,B](val derive: A => B)
  extends RxBufferBase[A,B]
  with RxBufferWatcher[A] 
  with Seq[B]