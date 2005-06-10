/*   Copyright 2004 The Apache Software Foundation
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.xmlbeans.impl.store;

import org.apache.xmlbeans.*;
import org.apache.xmlbeans.impl.common.XPath;
import org.w3c.dom.*;

import javax.xml.namespace.QName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Date;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;

public abstract class Query
{
    abstract XmlObject[] objectExecute(Cur c, XmlOptions options);

    abstract XmlCursor cursorExecute(Cur c, XmlOptions options);

    //
    // Xqrl store specific implementation of compiled path/query
    //

    static XmlObject[] objectExecQuery(Cur c, String queryExpr, XmlOptions options)
    {
        return getCompiledQuery(queryExpr, options).objectExecute(c, options);
    }

    static XmlCursor cursorExecQuery(Cur c, String queryExpr, XmlOptions options)
    {
        return getCompiledQuery(queryExpr, options).cursorExecute(c, options);
    }

    public static synchronized Query getCompiledQuery(String queryExpr, XmlOptions options)
    {
        return getCompiledQuery(queryExpr, Path.getCurrentNodeVar(options));
    }

    static synchronized Query getCompiledQuery(String queryExpr, String currentVar)
    {
        assert queryExpr != null;

        //Parse the query via XBeans: need to figure out end of prolog
        //in order to bind $this...not good but...
        Map boundary = new HashMap();
        int boundaryVal = 0;
        try {
            XPath.compileXPath(queryExpr, currentVar, boundary);
        }
        catch (XPath.XPathCompileException e) {
            //don't care if it fails, just care about boundary
        }
        finally {
            boundaryVal = boundary.get(XPath._NS_BOUNDARY) == null ?
                    0 :
                    ((Integer) boundary.get(XPath._NS_BOUNDARY)).intValue();

        }

        //try XQRL
        Query query = (Query) _xqrlQueryCache.get(queryExpr);
        if (query != null)
            return query;
        else {
            query = createXqrlCompiledQuery(queryExpr, currentVar);
            if (query != null)
                _xqrlQueryCache.put(queryExpr, query);
            else {
                //query is still null; look for Saxon
                query = (Query) _saxonQueryCache.get(queryExpr);
                if (query != null)
                    return query;
                else
                    query = SaxonQueryImpl.createSaxonCompiledQuery(queryExpr, currentVar, boundaryVal);
                if (query != null)
                    _saxonQueryCache.put(queryExpr, query);
            }
        }
            if (query == null)
                throw new RuntimeException("No query engine found");
            return query;
        }

    public static synchronized String compileQuery(String queryExpr, XmlOptions options)
    {
        getCompiledQuery(queryExpr, options);
        return queryExpr;
    }

    private static Query createXqrlCompiledQuery(String queryExpr, String currentVar)
    {
        if (_xqrlCompileQuery == null) {
            try {
                Class xqrlImpl = Class.forName("org.apache.xmlbeans.impl.store.XqrlImpl");

                _xqrlCompileQuery =
                        xqrlImpl.getDeclaredMethod("compileQuery",
                                new Class[]{String.class, String.class, Boolean.class});
            }
            catch (ClassNotFoundException e) {
                return null;
            }
            catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        Object[] args = new Object[]{queryExpr, currentVar, new Boolean(true)};

        try {
            return (Query) _xqrlCompileQuery.invoke(null, args);
        }
        catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            throw new RuntimeException(t.getMessage(), t);
        }
        catch (IllegalAccessException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }


    private static final class SaxonQueryImpl extends Query
    {

        private SaxonQueryImpl(SaxonXBeansDelegate.QueryInterface xqueryImpl)
        {
            _xqueryImpl = xqueryImpl;
        }

        public static Query createSaxonCompiledQuery(String queryExpr,
                                                     String currentVar,
                                                     int boundary)
        {
            assert !(currentVar.startsWith(".") || currentVar.startsWith(".."));

            SaxonXBeansDelegate.QueryInterface impl =
                    SaxonXBeansDelegate.createQueryInstance(queryExpr,
                            currentVar, boundary);
            if (impl == null)
                return null;

            return new SaxonQueryImpl(impl);
        }

        XmlObject[] objectExecute(Cur c, XmlOptions options)
        {
            return new SaxonQueryEngine(_xqueryImpl, c, options).objectExecute();
        }

        XmlCursor cursorExecute(Cur c, XmlOptions options)
        {
            return new SaxonQueryEngine(_xqueryImpl, c, options).cursorExecute();
        }


        private static class SaxonQueryEngine
        {
            public SaxonQueryEngine(SaxonXBeansDelegate.QueryInterface xqImpl,
                                    Cur c, XmlOptions opt)
            {

                _saxonImpl = xqImpl;
                _version = c._locale.version();
                _cur = c.weakCur(this);
                _options = opt;

            }

            public XmlObject[] objectExecute()
            {
                if (_cur != null && _version != _cur._locale.version())
                //throw new ConcurrentModificationException
                // ("Document changed during select")
                    ;

                List resultsList;
                resultsList = _saxonImpl.execQuery(_cur.getDom());

                assert resultsList.size() > -1;

                XmlObject[] result = new XmlObject[resultsList.size()];
                int i;
                for (i = 0; i < resultsList.size(); i++) {
                    //copy objects into the locale
                    Locale l = Locale.getLocale(_cur._locale._schemaTypeLoader,
                            _options);

                    l.enter();
                    Object node = resultsList.get(i);
                    Cur res = null;
                    try {
                        //typed function results of XQuery
                        if (!(node instanceof Node)) {
                            //TODO: exact same code as Path.java
                            //make a common super-class and pull this--what to name that
                            //superclass???
                            res = l.load("<xml-fragment>" +
                                    node.toString() +
                                    "</xml-fragment>").tempCur();
                            SchemaType type=getType(node);
                            Locale.autoTypeDocument(res, type, null);
                            result[i] = res.getObject();
                        }
                        else
                            res = loadNode(l, (Node) node);
                        result[i] = res.getObject();
                    }
                    catch (XmlException e) {
                        throw new RuntimeException(e);
                    }
                    finally {
                        l.exit();
                    }
                    res.release();
                }
                release();
                _saxonImpl = null;
                return result;
            }
            private SchemaType getType(Object node)
            {
                SchemaType type;
                if (node instanceof Integer)
                    type = XmlInteger.type;
                else if (node instanceof Double)
                    type = XmlDouble.type;
                else if (node instanceof Long)
                    type = XmlLong.type;
                else if (node instanceof Float)
                    type = XmlFloat.type;
                else if (node instanceof BigDecimal)
                    type = XmlDecimal.type;
                else if (node instanceof Boolean)
                    type = XmlBoolean.type;
                else if (node instanceof String)
                    type = XmlString.type;
                else if (node instanceof Date)
                    type = XmlDate.type;
                else
                    type = XmlAnySimpleType.type;
                return type;
            }
            public XmlCursor cursorExecute()
            {
                if (_cur != null && _version != _cur._locale.version())
                //throw new ConcurrentModificationException
                // ("Document changed during select")
                    ;

                List resultsList;
                resultsList = _saxonImpl.execQuery(_cur.getDom());

                assert resultsList.size() > -1;

                int i;
                _saxonImpl = null;

                Locale locale = Locale.getLocale(_cur._locale._schemaTypeLoader, _options);
                locale.enter();
                Locale.LoadContext _context = new Cur.CurLoadContext(locale, _options);
                Cursor resultCur = null;
                try {
                    for (i = 0; i < resultsList.size(); i++) {
                        loadNodeHelper(locale, (Node) resultsList.get(i), _context);
                    }
                    Cur c = _context.finish();
                    Locale.associateSourceName(c, _options);
                    Locale.autoTypeDocument(c, null, _options);
                    resultCur = new Cursor(c);
                }
                catch (Exception e) {
                }
                finally {
                    locale.exit();
                }
                release();
                return resultCur;
            }


            public void release()
            {
                if (_cur != null) {
                    _cur.release();
                    _cur = null;
                }
            }


            private Cur loadNode(Locale locale, Node node)
            {
                Locale.LoadContext context = new Cur.CurLoadContext(locale, _options);

                try {
                    loadNodeHelper(locale, node, context);
                    Cur c = context.finish();
                    Locale.associateSourceName(c, _options);
                    Locale.autoTypeDocument(c, null, _options);
                    return c;
                }
                catch (Exception e) {
                    throw new XmlRuntimeException(e.getMessage(), e);
                }
            }

            private void loadNodeHelper(Locale locale, Node node, Locale.LoadContext context)
            {
                if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
                    QName attName = new QName(node.getNamespaceURI(),
                            node.getLocalName(),
                            node.getPrefix());
                    context.attr(attName, node.getNodeValue());
                }
                else
                    locale.loadNode(node, context);

            }


            private Cur _cur;
            private SaxonXBeansDelegate.QueryInterface _saxonImpl;
            private long _version;
            private XmlOptions _options;
        }

        private SaxonXBeansDelegate.QueryInterface _xqueryImpl;
    }


    private static HashMap _xqrlQueryCache = new HashMap(); //todo check for memory leaks
    private static HashMap _saxonQueryCache = new HashMap();

    private static Method _xqrlCompileQuery;
}
