/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.smartloli.kafka.eagle.controller;

import java.io.OutputStream;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import org.smartloli.kafka.eagle.factory.KafkaFactory;
import org.smartloli.kafka.eagle.factory.KafkaService;
import org.smartloli.kafka.eagle.service.TopicService;
import org.smartloli.kafka.eagle.util.GzipUtils;

/**
 * Kafka topic controller to viewer data.
 * 
 * @author smartloli.
 *
 *         Created by Sep 6, 2016
 */
@Controller
public class TopicController {

	private final static Logger LOG = LoggerFactory.getLogger(TopicController.class);

	/** Kafka topic service interface. */
	@Autowired
	private TopicService topicService;

	/** Kafka service interface. */
	private KafkaService kafkaService = new KafkaFactory().create();

	/** Topic create viewer. */
	@RequestMapping(value = "/topic/create", method = RequestMethod.GET)
	public ModelAndView topicCreateView(HttpSession session) {
		ModelAndView mav = new ModelAndView();
		mav.setViewName("/topic/create");
		return mav;
	}

	/** Topic list viewer. */
	@RequestMapping(value = "/topic/list", method = RequestMethod.GET)
	public ModelAndView topicListView(HttpSession session) {
		ModelAndView mav = new ModelAndView();
		mav.setViewName("/topic/list");
		return mav;
	}

	/** Topic metadata viewer. */
	@RequestMapping(value = "/topic/meta/{tname}/", method = RequestMethod.GET)
	public ModelAndView topicMetaView(@PathVariable("tname") String tname, HttpServletRequest request) {
		String ip = request.getHeader("x-forwarded-for");
		LOG.info("IP:" + (ip == null ? request.getRemoteAddr() : ip));

		ModelAndView mav = new ModelAndView();
		if (topicService.hasTopic(tname, ip)) {
			mav.setViewName("/topic/topic_meta");
		} else {
			mav.setViewName("/error/404");
		}

		return mav;
	}

	/** Create topic success viewer. */
	@RequestMapping(value = "/topic/create/success", method = RequestMethod.GET)
	public ModelAndView successView(HttpSession session) {
		ModelAndView mav = new ModelAndView();
		mav.setViewName("/topic/add_success");
		return mav;
	}

	/** Create topic failed viewer. */
	@RequestMapping(value = "/topic/create/failed", method = RequestMethod.GET)
	public ModelAndView failedView(HttpSession session) {
		ModelAndView mav = new ModelAndView();
		mav.setViewName("/topic/add_failed");
		return mav;
	}

	/** Get topic metadata by ajax. */
	@RequestMapping(value = "/topic/meta/{tname}/ajax", method = RequestMethod.GET)
	public void topicMetaAjax(@PathVariable("tname") String tname, HttpServletResponse response, HttpServletRequest request) {
		response.setContentType("text/html;charset=utf-8");
		response.setCharacterEncoding("utf-8");
		response.setHeader("Charset", "utf-8");
		response.setHeader("Cache-Control", "no-cache");
		response.setHeader("Content-Encoding", "gzip");

		String aoData = request.getParameter("aoData");
		JSONArray params = JSON.parseArray(aoData);
		int sEcho = 0, iDisplayStart = 0, iDisplayLength = 0;
		for (Object object : params) {
			JSONObject param = (JSONObject) object;
			if ("sEcho".equals(param.getString("name"))) {
				sEcho = param.getIntValue("value");
			} else if ("iDisplayStart".equals(param.getString("name"))) {
				iDisplayStart = param.getIntValue("value");
			} else if ("iDisplayLength".equals(param.getString("name"))) {
				iDisplayLength = param.getIntValue("value");
			}
		}

		String metadata = topicService.metadata(tname);
		JSONArray metadatas = JSON.parseArray(metadata);
		int offset = 0;
		JSONArray aaDatas = new JSONArray();
		for (Object object : metadatas) {
			JSONObject meta = (JSONObject) object;
			if (offset < (iDisplayLength + iDisplayStart) && offset >= iDisplayStart) {
				JSONObject obj = new JSONObject();
				obj.put("topic", tname);
				obj.put("partition", meta.getInteger("partitionId"));
				obj.put("leader", meta.getInteger("leader"));
				obj.put("replicas", meta.getString("replicas"));
				obj.put("isr", meta.getString("isr"));
				aaDatas.add(obj);
			}
			offset++;
		}

		JSONObject target = new JSONObject();
		target.put("sEcho", sEcho);
		target.put("iTotalRecords", metadatas.size());
		target.put("iTotalDisplayRecords", metadatas.size());
		target.put("aaData", aaDatas);
		try {
			byte[] output = GzipUtils.compressToByte(target.toJSONString());
			response.setContentLength(output == null ? "NULL".toCharArray().length : output.length);
			OutputStream out = response.getOutputStream();
			out.write(output);

			out.flush();
			out.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	/** Get topic datasets by ajax. */
	@RequestMapping(value = "/topic/list/table/ajax", method = RequestMethod.GET)
	public void topicListAjax(HttpServletResponse response, HttpServletRequest request) {
		response.setContentType("text/html;charset=utf-8");
		response.setCharacterEncoding("utf-8");
		response.setHeader("Charset", "utf-8");
		response.setHeader("Cache-Control", "no-cache");
		response.setHeader("Content-Encoding", "gzip");

		String aoData = request.getParameter("aoData");
		JSONArray params = JSON.parseArray(aoData);
		int sEcho = 0, iDisplayStart = 0, iDisplayLength = 0;
		String search = "";
		for (Object object : params) {
			JSONObject param = (JSONObject) object;
			if ("sEcho".equals(param.getString("name"))) {
				sEcho = param.getIntValue("value");
			} else if ("iDisplayStart".equals(param.getString("name"))) {
				iDisplayStart = param.getIntValue("value");
			} else if ("iDisplayLength".equals(param.getString("name"))) {
				iDisplayLength = param.getIntValue("value");
			} else if ("sSearch".equals(param.getString("name"))) {
				search = param.getString("value");
			}
		}

		JSONArray topics = JSON.parseArray(topicService.list());
		int offset = 0;
		JSONArray aaDatas = new JSONArray();
		for (Object object : topics) {
			JSONObject topic = (JSONObject) object;
			if (search.length() > 0 && search.equals(topic.getString("topic"))) {
				JSONObject obj = new JSONObject();
				obj.put("id", topic.getInteger("id"));
				obj.put("topic", "<a href='/ke/topic/meta/" + topic.getString("topic") + "/' target='_blank'>" + topic.getString("topic") + "</a>");
				obj.put("partitions", topic.getString("partitions").length() > 50 ? topic.getString("partitions").substring(0, 50) + "..." : topic.getString("partitions"));
				obj.put("partitionNumbers", topic.getInteger("partitionNumbers"));
				obj.put("created", topic.getString("created"));
				obj.put("modify", topic.getString("modify"));
				aaDatas.add(obj);
			} else if (search.length() == 0) {
				if (offset < (iDisplayLength + iDisplayStart) && offset >= iDisplayStart) {
					JSONObject obj = new JSONObject();
					obj.put("id", topic.getInteger("id"));
					obj.put("topic", "<a href='/ke/topic/meta/" + topic.getString("topic") + "/' target='_blank'>" + topic.getString("topic") + "</a>");
					obj.put("partitions", topic.getString("partitions").length() > 50 ? topic.getString("partitions").substring(0, 50) + "..." : topic.getString("partitions"));
					obj.put("partitionNumbers", topic.getInteger("partitionNumbers"));
					obj.put("created", topic.getString("created"));
					obj.put("modify", topic.getString("modify"));
					aaDatas.add(obj);
				}
				offset++;
			}
		}

		JSONObject target = new JSONObject();
		target.put("sEcho", sEcho);
		target.put("iTotalRecords", topics.size());
		target.put("iTotalDisplayRecords", topics.size());
		target.put("aaData", aaDatas);
		try {
			byte[] output = GzipUtils.compressToByte(target.toJSONString());
			response.setContentLength(output.length);
			OutputStream out = response.getOutputStream();
			out.write(output);

			out.flush();
			out.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	/** Create topic form. */
	@RequestMapping(value = "/topic/create/form", method = RequestMethod.POST)
	public ModelAndView topicAddForm(HttpSession session, HttpServletResponse response, HttpServletRequest request) {
		ModelAndView mav = new ModelAndView();
		String ke_topic_name = request.getParameter("ke_topic_name");
		String ke_topic_partition = request.getParameter("ke_topic_partition");
		String ke_topic_repli = request.getParameter("ke_topic_repli");
		Map<String, Object> respons = kafkaService.create(ke_topic_name, ke_topic_partition, ke_topic_repli);
		if ("success".equals(respons.get("status"))) {
			session.removeAttribute("Submit_Status");
			session.setAttribute("Submit_Status", respons.get("info"));
			mav.setViewName("redirect:/topic/create/success");
		} else {
			session.removeAttribute("Submit_Status");
			session.setAttribute("Submit_Status", respons.get("info"));
			mav.setViewName("redirect:/topic/create/failed");
		}
		return mav;
	}

}
