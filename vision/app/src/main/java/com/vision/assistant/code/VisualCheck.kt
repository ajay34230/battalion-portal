package com.vision.assistant.code

import org.json.JSONObject

/** Reads the numbers the preview page reports about itself and turns them into plain-language findings. */
object VisualCheck {
    /** JavaScript run inside the page. Returns a JSON string. */
    const val SCRIPT = """(function(){
  var w=window.innerWidth, out={w:w, sw:document.documentElement.scrollWidth, wide:[], noAlt:0, tiny:0, smallTargets:0, hasViewport:!!document.querySelector('meta[name=viewport]'), blank:(document.body&&document.body.innerText.trim().length===0&&document.images.length===0)};
  var all=document.body?document.body.getElementsByTagName('*'):[];
  for(var i=0;i<all.length&&i<2000;i++){var e=all[i],r=e.getBoundingClientRect();
    if(r.width>0&&r.right>w+1&&out.wide.length<5)out.wide.push(e.tagName.toLowerCase()+(e.className&&typeof e.className==='string'?'.'+e.className.split(' ')[0]:''));
    var cs=getComputedStyle(e);
    if(e.childNodes.length&&e.firstChild.nodeType===3&&e.firstChild.nodeValue.trim().length>2&&parseFloat(cs.fontSize)<11)out.tiny++;
    if((e.tagName==='BUTTON'||e.tagName==='A'||e.tagName==='INPUT')&&r.width>0&&(r.height<32||r.width<32))out.smallTargets++;
    if(e.tagName==='IMG'&&!e.getAttribute('alt'))out.noAlt++;}
  return JSON.stringify(out);})()"""

    data class Finding(val severity: String, val text: String)

    fun interpret(json: String, requestedWidth: Int): List<Finding> {
        val o = try { JSONObject(json) } catch (_: Exception) { return listOf(Finding("error", "The page did not report back (script error or page not loaded).")) }
        val out = ArrayList<Finding>()
        if (o.optBoolean("blank")) out.add(Finding("error", "The page appears blank at ${requestedWidth}px."))
        if (o.optInt("sw") > o.optInt("w") + 1) out.add(Finding("error", "Sideways scrolling at ${requestedWidth}px: the page is ${o.optInt("sw")}px wide but the screen is ${o.optInt("w")}px." + o.optJSONArray("wide")?.let { a -> if (a.length() > 0) " Overflowing: " + (0 until a.length()).joinToString(", ") { a.getString(it) } else "" }.orEmpty()))
        if (!o.optBoolean("hasViewport")) out.add(Finding("warn", "No <meta name=\"viewport\"> tag: phones will show the page zoomed out."))
        if (o.optInt("tiny") > 0) out.add(Finding("warn", "${o.optInt("tiny")} text element(s) smaller than 11px are hard to read."))
        if (o.optInt("smallTargets") > 0) out.add(Finding("warn", "${o.optInt("smallTargets")} button/link/input(s) smaller than 32px are hard to tap."))
        if (o.optInt("noAlt") > 0) out.add(Finding("warn", "${o.optInt("noAlt")} image(s) have no alt text (screen readers cannot describe them)."))
        return out
    }

    /** Fraction of pixels that differ from the most common colour; ~0 means an empty/blank render. */
    fun inkRatio(pixels: IntArray): Double {
        if (pixels.isEmpty()) return 0.0
        val counts = HashMap<Int, Int>(); for (p in pixels) counts.merge(p, 1, Int::plus)
        val most = counts.values.max()
        return 1.0 - most.toDouble() / pixels.size
    }
}
