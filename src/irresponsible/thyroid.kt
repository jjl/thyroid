package irresponsible.thyroid

import clojure.lang.IFn
import clojure.lang.IObj
import clojure.lang.IPersistentMap
import org.thymeleaf.context.ITemplateContext
import org.thymeleaf.dialect.AbstractProcessorDialect
import org.thymeleaf.engine.AttributeName
import org.thymeleaf.model.IProcessableElementTag
import org.thymeleaf.processor.IProcessor
import org.thymeleaf.processor.element.AbstractElementTagProcessor
import org.thymeleaf.processor.element.AbstractAttributeTagProcessor
import org.thymeleaf.processor.element.IElementTagStructureHandler
import org.thymeleaf.standard.StandardDialect
import org.thymeleaf.templatemode.TemplateMode

@Suppress("UNUSED")
class ClojureTagProcessor(
  val _dialectPrefix: String,
  val _tagName: String,
  val _usePrefix: Boolean,
  val _precedence: Int,
  val _handler: IFn,
  val _meta: IPersistentMap? = null
) : AbstractElementTagProcessor(TemplateMode.HTML, _dialectPrefix, _tagName, _usePrefix, null, false, _precedence)
  , IObj {
  override fun meta(): IPersistentMap? = _meta
  override fun withMeta(meta: IPersistentMap?): IObj = ClojureTagProcessor(dialectPrefix, _tagName, _usePrefix, _precedence, _handler, meta)
  override fun doProcess(ctx: ITemplateContext, tag: IProcessableElementTag, h: IElementTagStructureHandler) {
    _handler.invoke(ctx,tag,h)
  }

}

@Suppress("UNUSED")
class ClojureAttrProcessor(
  val _dialectPrefix: String,
  val _tagName: String?,
  val _attrName: String,
  val _useTagPrefix: Boolean,
  val _useAttrPrefix: Boolean,
  val _remove: Boolean,
  val _precedence: Int,
  val _handler: IFn,
  val _meta: IPersistentMap? = null
) : AbstractAttributeTagProcessor(TemplateMode.HTML, _dialectPrefix, _tagName, _useTagPrefix, _attrName, _useAttrPrefix, _precedence, _remove)
  , IObj {
  override fun meta(): IPersistentMap? = _meta
  override fun withMeta(meta: IPersistentMap?): IObj = ClojureAttrProcessor(_dialectPrefix, _tagName, _attrName, _useTagPrefix, _useAttrPrefix, _remove, _precedence, _handler, meta)
  override fun doProcess(ctx: ITemplateContext, tag: IProcessableElementTag, attrName: AttributeName, attrValue: String, sh: IElementTagStructureHandler) {
    _handler.invoke(ctx, tag, attrName, attrValue, sh)
  }
}

@Suppress("UNUSED")
class ClojureDialect(
  val _name: String,
  val _prefix: String,
  val _getProcessors: IFn,
  val _precedence: Int = StandardDialect.PROCESSOR_PRECEDENCE,
  val _meta: IPersistentMap? = null
) : AbstractProcessorDialect(_name, _prefix, _precedence), IObj {
  override fun meta(): IPersistentMap? = _meta
  override fun withMeta(meta: IPersistentMap?): IObj = ClojureDialect(_name, _prefix,_getProcessors, _precedence,meta)
  override fun getProcessors(dialectPrefix: String) : Set<IProcessor> {
    val ps = _getProcessors.invoke(dialectPrefix) as? Set<*>
      ?: throw IllegalStateException("handler did not return a set")
    return ps.map { it as? IProcessor ?: throw IllegalStateException("An item in the set was not an IProcessor") }.toSet()
  }
}

