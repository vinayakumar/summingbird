/*
Copyright 2013 Twitter, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.twitter.summingbird.online

import com.twitter.util.{ Await, Duration, Future, Try }

import java.util.Queue
import java.util.concurrent.{ArrayBlockingQueue, BlockingQueue, LinkedBlockingQueue, TimeUnit}
import java.util.concurrent.ConcurrentLinkedQueue
/**
 *
 * @author Oscar Boykin
 */

object FutureChannel {
  /**
   * By default, don't block on put (the future has completed
   * by then).
   */
  def apply[I,O]() = linkedNonBlocking[I, O]

  def arrayBlocking[I,O](size: Int): FutureChannel[I,O] =
    new FutureChannel[I,O](new ArrayBlockingQueue(size))

  def linkedBlocking[I,O]: FutureChannel[I,O] =
    new FutureChannel[I,O](new LinkedBlockingQueue())

  def linkedNonBlocking[I,O]: FutureChannel[I,O] =
    new FutureChannel[I,O](new ConcurrentLinkedQueue())
}

/**
 * Use this class with a thread-safe queue to receive
 * results from futures in one thread.
 * Storm needs us to touch it's code in one event path (via
 * the execute method in bolts)
 */
class FutureChannel[I,O] private (queue: Queue[(I,Try[O])]) {

  def call(in: I)(fn: I => Future[O]): Unit = put(in, fn(in))

  def put(in: I, f: Future[O]): Unit = f.respond { t => queue.add((in, t)) }

  /**
   * check if something is ready now
   */
  def poll: Option[(I, Try[O])] = Option(queue.poll())

  // Do something on all the elements ready:
  @annotation.tailrec
  final def foreach(fn: ((I, Try[O])) => Unit): Unit =
    queue.poll() match {
      case null => ()
      case itt => fn(itt); foreach(fn)
    }

  @annotation.tailrec
  final def foldLeft[V](init: V)(fn: (V, (I, Try[O])) => V): V = {
    queue.poll() match {
      case null => init
      case itt => foldLeft(fn(init, itt))(fn)
    }
  }
}
