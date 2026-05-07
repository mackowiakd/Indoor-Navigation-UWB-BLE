--
-- PostgreSQL database dump
--

-- Dumped from database version 15.17
-- Dumped by pg_dump version 17.0

-- Started on 2026-05-07 11:13:23

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- TOC entry 216 (class 1259 OID 24708)
-- Name: dim_iot_devices; Type: TABLE; Schema: public; Owner: domi
--

CREATE TABLE public.dim_iot_devices (
    mac_address character varying(17) NOT NULL,
    device_type character varying(20) NOT NULL,
    location_id integer,
    semantic_role character varying(100),
    tx_power_config integer
);


ALTER TABLE public.dim_iot_devices OWNER TO domi;

--
-- TOC entry 215 (class 1259 OID 24702)
-- Name: dim_topology; Type: TABLE; Schema: public; Owner: domi
--

CREATE TABLE public.dim_topology (
    location_id integer NOT NULL,
    building character varying(50) NOT NULL,
    wing character varying(50),
    floor integer NOT NULL,
    room_name character varying(100)
);


ALTER TABLE public.dim_topology OWNER TO domi;

--
-- TOC entry 214 (class 1259 OID 24701)
-- Name: dim_topology_location_id_seq; Type: SEQUENCE; Schema: public; Owner: domi
--

CREATE SEQUENCE public.dim_topology_location_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.dim_topology_location_id_seq OWNER TO domi;

--
-- TOC entry 3433 (class 0 OID 0)
-- Dependencies: 214
-- Name: dim_topology_location_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: domi
--

ALTER SEQUENCE public.dim_topology_location_id_seq OWNED BY public.dim_topology.location_id;


--
-- TOC entry 218 (class 1259 OID 24719)
-- Name: fact_telemetry; Type: TABLE; Schema: public; Owner: domi
--

CREATE TABLE public.fact_telemetry (
    telemetry_id integer NOT NULL,
    "timestamp" timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    scanner_id character varying(50),
    mac_address character varying(17),
    uwb_distance_m numeric(5,2),
    ble_rssi_dbm integer,
    is_ignored_by_filter boolean
);


ALTER TABLE public.fact_telemetry OWNER TO domi;

--
-- TOC entry 217 (class 1259 OID 24718)
-- Name: fact_telemetry_telemetry_id_seq; Type: SEQUENCE; Schema: public; Owner: domi
--

CREATE SEQUENCE public.fact_telemetry_telemetry_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.fact_telemetry_telemetry_id_seq OWNER TO domi;

--
-- TOC entry 3434 (class 0 OID 0)
-- Dependencies: 217
-- Name: fact_telemetry_telemetry_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: domi
--

ALTER SEQUENCE public.fact_telemetry_telemetry_id_seq OWNED BY public.fact_telemetry.telemetry_id;


--
-- TOC entry 3269 (class 2604 OID 24705)
-- Name: dim_topology location_id; Type: DEFAULT; Schema: public; Owner: domi
--

ALTER TABLE ONLY public.dim_topology ALTER COLUMN location_id SET DEFAULT nextval('public.dim_topology_location_id_seq'::regclass);


--
-- TOC entry 3270 (class 2604 OID 24722)
-- Name: fact_telemetry telemetry_id; Type: DEFAULT; Schema: public; Owner: domi
--

ALTER TABLE ONLY public.fact_telemetry ALTER COLUMN telemetry_id SET DEFAULT nextval('public.fact_telemetry_telemetry_id_seq'::regclass);


--
-- TOC entry 3425 (class 0 OID 24708)
-- Dependencies: 216
-- Data for Name: dim_iot_devices; Type: TABLE DATA; Schema: public; Owner: domi
--

COPY public.dim_iot_devices (mac_address, device_type, location_id, semantic_role, tx_power_config) FROM stdin;
aa:bb:cc:dd:ee:ff	BLE_BEACON	1	Ekspres do kawy	-59
0x0001	UWB_ANCHOR	2	Kotwica UWB - Narożnik Lewy	\N
0x0002	UWB_ANCHOR	2	Kotwica UWB - Narożnik Prawy	\N
ff:ff:12:b1:64:d1	BLE_BEACON	2	Tag BLE - Desk	-59
a8:03:2a:b8:ee:fa	BLE_BEACON	2	Tag BLE - Window	-59
\.


--
-- TOC entry 3424 (class 0 OID 24702)
-- Dependencies: 215
-- Data for Name: dim_topology; Type: TABLE DATA; Schema: public; Owner: domi
--

COPY public.dim_topology (location_id, building, wing, floor, room_name) FROM stdin;
1	AEI	Lewe	1	Korytarz Główny
2	My house	Main	1	Testing space
\.


--
-- TOC entry 3427 (class 0 OID 24719)
-- Dependencies: 218
-- Data for Name: fact_telemetry; Type: TABLE DATA; Schema: public; Owner: domi
--

COPY public.fact_telemetry (telemetry_id, "timestamp", scanner_id, mac_address, uwb_distance_m, ble_rssi_dbm, is_ignored_by_filter) FROM stdin;
1	2026-02-07 02:55:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
2	2026-02-06 23:52:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-63	f
3	2026-02-06 23:28:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
4	2026-02-06 19:54:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
5	2026-02-06 22:59:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
6	2026-02-06 23:17:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-63	f
7	2026-02-06 20:36:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-63	f
8	2026-02-06 21:05:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-63	f
9	2026-02-06 21:19:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
10	2026-02-07 05:47:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
11	2026-02-07 03:27:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
12	2026-02-07 03:39:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
13	2026-02-06 20:26:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
14	2026-02-07 04:49:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-63	f
15	2026-02-08 01:48:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
16	2026-02-08 02:28:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
17	2026-02-07 21:00:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
18	2026-02-08 05:13:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-63	f
19	2026-02-07 23:19:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
20	2026-02-07 23:08:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
21	2026-02-07 20:03:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
22	2026-02-07 21:41:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
23	2026-02-08 01:53:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
24	2026-02-07 19:41:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-63	f
25	2026-02-08 04:03:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
26	2026-02-08 02:41:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
27	2026-02-07 19:26:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
28	2026-02-07 20:59:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
29	2026-02-08 04:54:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
30	2026-02-08 21:30:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
31	2026-02-09 00:47:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
32	2026-02-08 22:07:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
33	2026-02-09 02:21:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-63	f
34	2026-02-08 19:18:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
35	2026-02-09 01:04:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
36	2026-02-08 23:50:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
37	2026-02-08 19:33:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-63	f
38	2026-02-09 02:25:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
39	2026-02-09 05:36:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
40	2026-02-09 04:28:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-63	f
41	2026-02-08 21:29:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
42	2026-02-09 04:49:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
43	2026-02-09 02:00:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
44	2026-02-09 23:16:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
45	2026-02-10 00:21:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-63	f
46	2026-02-09 21:28:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
47	2026-02-10 03:59:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-63	f
48	2026-02-10 01:05:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-63	f
49	2026-02-09 21:53:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-63	f
50	2026-02-10 01:19:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
51	2026-02-11 00:48:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
52	2026-02-10 23:37:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
53	2026-02-10 20:43:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-63	f
54	2026-02-11 02:41:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-63	f
55	2026-02-10 19:14:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
56	2026-02-10 20:27:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
57	2026-02-11 03:44:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
58	2026-02-10 20:26:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
59	2026-02-10 20:04:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
60	2026-02-11 00:21:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
61	2026-02-11 04:46:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
62	2026-02-10 20:17:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-63	f
63	2026-02-10 19:03:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
64	2026-02-11 02:16:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-63	f
65	2026-02-10 19:21:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
66	2026-02-11 22:51:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
67	2026-02-11 22:05:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
68	2026-02-12 04:09:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
69	2026-02-12 02:36:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
70	2026-02-12 05:33:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
71	2026-02-12 02:52:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
72	2026-02-12 01:49:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-63	f
73	2026-02-11 21:59:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
74	2026-02-11 23:44:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
75	2026-02-11 21:12:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-63	f
76	2026-02-11 23:17:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
77	2026-02-12 01:22:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
78	2026-02-11 21:03:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
79	2026-02-12 03:53:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
80	2026-02-12 21:06:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
81	2026-02-13 02:50:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
82	2026-02-12 20:20:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
83	2026-02-12 23:12:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
84	2026-02-12 22:29:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-63	f
85	2026-02-13 05:08:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
86	2026-02-13 02:37:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
87	2026-02-13 01:34:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-63	f
88	2026-02-13 20:00:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
89	2026-02-14 03:29:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-63	f
90	2026-02-13 19:14:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
91	2026-02-13 20:04:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-63	f
92	2026-02-14 02:36:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
93	2026-02-13 23:24:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
94	2026-02-15 04:25:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
95	2026-02-14 21:50:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
96	2026-02-14 23:28:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
97	2026-02-14 23:42:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
98	2026-02-14 21:45:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-63	f
99	2026-02-15 05:42:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
100	2026-02-14 19:35:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
101	2026-02-15 00:02:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
102	2026-02-14 20:49:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-63	f
103	2026-02-15 01:05:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
104	2026-02-15 20:53:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
105	2026-02-15 20:28:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
106	2026-02-15 20:53:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
107	2026-02-16 01:06:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-63	f
108	2026-02-15 21:51:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
109	2026-02-15 21:35:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
110	2026-02-16 04:37:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
111	2026-02-15 23:47:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-63	f
112	2026-02-15 19:34:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-63	f
113	2026-02-15 19:28:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
114	2026-02-15 20:45:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-63	f
115	2026-02-16 02:26:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
116	2026-02-15 22:21:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
117	2026-02-16 01:27:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
118	2026-02-16 22:32:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
119	2026-02-16 20:46:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
120	2026-02-17 00:36:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
121	2026-02-16 21:35:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
122	2026-02-16 20:00:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
123	2026-02-17 04:19:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
124	2026-02-17 23:46:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
125	2026-02-18 05:50:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
126	2026-02-18 00:29:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
127	2026-02-18 05:39:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
128	2026-02-17 20:46:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
129	2026-02-18 05:38:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
130	2026-02-17 23:03:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
131	2026-02-18 01:48:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
132	2026-02-17 22:54:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
133	2026-02-17 19:25:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
134	2026-02-18 20:06:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
135	2026-02-19 02:48:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
136	2026-02-18 20:13:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
137	2026-02-18 22:03:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
138	2026-02-18 18:59:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
139	2026-02-19 00:07:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
140	2026-02-19 01:48:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
141	2026-02-18 21:54:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
142	2026-02-19 20:44:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
143	2026-02-20 00:41:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
144	2026-02-20 04:25:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
145	2026-02-19 20:27:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
146	2026-02-19 19:58:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
147	2026-02-19 20:44:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
148	2026-02-20 05:37:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
149	2026-02-19 19:50:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
150	2026-02-20 05:04:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
151	2026-02-20 00:06:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
152	2026-02-19 19:14:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
153	2026-02-20 00:40:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
154	2026-02-19 22:19:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
155	2026-02-20 02:46:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
156	2026-02-20 04:36:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
157	2026-02-21 03:11:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
158	2026-02-21 02:18:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
159	2026-02-20 20:27:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
160	2026-02-20 19:51:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
161	2026-02-20 19:29:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
162	2026-02-20 23:30:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
163	2026-02-20 18:58:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
164	2026-02-21 19:43:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
165	2026-02-21 21:46:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
166	2026-02-22 04:14:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
167	2026-02-21 18:55:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
168	2026-02-21 21:49:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
169	2026-02-22 01:22:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
170	2026-02-21 21:33:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
171	2026-02-22 04:59:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
172	2026-02-22 04:36:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
173	2026-02-21 23:33:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
174	2026-02-21 22:22:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
175	2026-02-21 19:21:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
176	2026-02-22 00:09:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
177	2026-02-22 23:45:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
178	2026-02-22 23:47:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
179	2026-02-22 20:27:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
180	2026-02-23 05:10:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
181	2026-02-23 05:08:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
182	2026-02-22 22:29:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
183	2026-02-22 23:40:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
184	2026-02-22 20:29:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
185	2026-02-22 21:26:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
186	2026-02-24 05:10:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
187	2026-02-23 20:34:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
188	2026-02-23 19:48:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
189	2026-02-23 21:31:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
190	2026-02-24 00:27:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
191	2026-02-23 19:42:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
192	2026-02-24 03:19:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
193	2026-02-23 22:47:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
194	2026-02-24 20:12:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
195	2026-02-25 02:01:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
196	2026-02-25 00:14:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
197	2026-02-25 02:44:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
198	2026-02-25 04:04:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
199	2026-02-25 03:04:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
200	2026-02-25 05:28:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
201	2026-02-25 00:32:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
202	2026-02-25 01:57:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
203	2026-02-24 21:22:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
204	2026-02-26 02:56:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
205	2026-02-26 02:13:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
206	2026-02-25 20:46:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
207	2026-02-25 21:32:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
208	2026-02-26 05:27:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
209	2026-02-26 04:04:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
210	2026-02-25 23:37:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
211	2026-02-26 00:37:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
212	2026-02-26 04:38:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
213	2026-02-25 23:52:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
214	2026-02-26 05:20:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-64	f
215	2026-02-26 03:55:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
216	2026-02-27 04:26:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
217	2026-02-26 21:25:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
218	2026-02-26 21:13:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
219	2026-02-26 22:10:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
220	2026-02-26 23:53:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
221	2026-02-27 01:01:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
222	2026-02-26 22:16:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
223	2026-02-27 20:05:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
224	2026-02-27 19:31:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
225	2026-02-28 04:21:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
226	2026-02-27 20:01:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
227	2026-02-27 19:39:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
228	2026-02-27 19:52:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
229	2026-02-27 23:32:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
230	2026-02-28 02:31:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
231	2026-02-28 00:24:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
232	2026-02-27 22:10:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
233	2026-02-28 04:04:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
234	2026-02-27 22:17:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
235	2026-02-28 04:31:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
236	2026-02-27 23:40:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
237	2026-03-01 05:44:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
238	2026-03-01 01:48:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
239	2026-03-01 02:20:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
240	2026-02-28 19:03:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
241	2026-02-28 18:58:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
242	2026-03-01 00:04:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
243	2026-02-28 19:14:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
244	2026-03-01 04:44:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
245	2026-02-28 19:46:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
246	2026-03-01 05:15:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
247	2026-03-01 01:23:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
248	2026-03-01 04:47:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
249	2026-02-28 21:27:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
250	2026-03-02 05:22:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
251	2026-03-01 22:53:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
252	2026-03-01 21:13:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
253	2026-03-02 03:29:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
254	2026-03-02 01:56:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
255	2026-03-02 00:17:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
256	2026-03-02 01:11:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
257	2026-03-02 05:09:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
258	2026-03-01 20:06:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
259	2026-03-01 21:54:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
260	2026-03-03 04:01:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
261	2026-03-02 23:36:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
262	2026-03-03 01:32:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
263	2026-03-03 02:15:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
264	2026-03-03 03:32:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
265	2026-03-04 01:35:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
266	2026-03-03 23:41:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
267	2026-03-03 22:46:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
268	2026-03-04 04:48:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
269	2026-03-03 21:08:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
270	2026-03-04 03:51:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
271	2026-03-04 01:05:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
272	2026-03-04 05:07:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
273	2026-03-04 03:11:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
274	2026-03-03 21:08:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
275	2026-03-04 05:04:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
276	2026-03-03 19:43:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
277	2026-03-04 03:22:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
278	2026-03-04 03:58:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
279	2026-03-04 22:35:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
280	2026-03-04 20:14:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
281	2026-03-05 04:31:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
282	2026-03-05 00:15:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
283	2026-03-05 00:36:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
284	2026-03-05 05:15:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
285	2026-03-04 23:37:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
286	2026-03-05 04:27:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
287	2026-03-04 23:43:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
288	2026-03-05 02:48:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
289	2026-03-05 03:15:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
290	2026-03-05 00:19:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
291	2026-03-05 22:35:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
292	2026-03-06 02:56:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
293	2026-03-05 22:12:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
294	2026-03-06 04:14:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
295	2026-03-05 19:39:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
296	2026-03-06 01:13:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
297	2026-03-06 04:48:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
298	2026-03-05 21:13:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
299	2026-03-05 22:34:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
300	2026-03-05 22:12:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
301	2026-03-06 02:58:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
302	2026-03-06 04:14:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
303	2026-03-05 23:50:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
304	2026-03-06 02:00:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
305	2026-03-06 19:50:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
306	2026-03-07 01:27:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
307	2026-03-06 20:37:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
308	2026-03-07 00:18:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
309	2026-03-07 05:49:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
310	2026-03-07 01:57:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
311	2026-03-06 20:26:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
312	2026-03-06 20:36:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
313	2026-03-07 03:38:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
314	2026-03-07 05:39:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
315	2026-03-07 00:19:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
316	2026-03-08 03:13:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
317	2026-03-07 23:05:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
318	2026-03-08 00:57:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
319	2026-03-07 20:29:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
320	2026-03-08 05:16:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
321	2026-03-08 05:21:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-65	f
322	2026-03-08 00:27:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
323	2026-03-07 23:45:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
324	2026-03-07 22:16:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
325	2026-03-08 01:57:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
326	2026-03-08 05:14:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
327	2026-03-09 02:50:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
328	2026-03-08 22:31:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
329	2026-03-09 02:24:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
330	2026-03-09 04:46:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
331	2026-03-09 03:44:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
332	2026-03-08 20:22:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
333	2026-03-08 21:50:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
334	2026-03-08 22:06:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
335	2026-03-09 04:04:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
336	2026-03-09 05:19:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
337	2026-03-09 21:24:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
338	2026-03-10 01:56:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
339	2026-03-10 05:25:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
340	2026-03-09 18:53:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
341	2026-03-10 05:47:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
342	2026-03-10 00:33:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
343	2026-03-09 20:37:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
344	2026-03-09 23:06:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
345	2026-03-10 00:32:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
346	2026-03-11 05:47:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
347	2026-03-11 00:20:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
348	2026-03-11 02:37:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
349	2026-03-11 03:45:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
350	2026-03-10 20:25:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
351	2026-03-12 02:49:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
352	2026-03-11 21:11:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
353	2026-03-12 02:08:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
354	2026-03-11 22:52:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
355	2026-03-12 03:07:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
356	2026-03-11 23:51:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
357	2026-03-13 02:29:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
358	2026-03-13 05:43:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
359	2026-03-13 04:07:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
360	2026-03-13 00:16:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
361	2026-03-13 03:58:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
362	2026-03-12 23:51:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
363	2026-03-13 01:28:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
364	2026-03-13 05:07:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
365	2026-03-13 03:05:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
366	2026-03-12 23:56:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
367	2026-03-12 20:26:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
368	2026-03-13 00:48:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
369	2026-03-12 22:20:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
370	2026-03-14 05:32:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
371	2026-03-13 21:52:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
372	2026-03-14 01:00:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
373	2026-03-13 20:47:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
374	2026-03-13 23:20:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
375	2026-03-13 19:13:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
376	2026-03-14 05:04:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
377	2026-03-14 05:22:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
378	2026-03-14 03:48:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
379	2026-03-13 22:35:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
380	2026-03-13 19:49:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
381	2026-03-14 02:11:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
382	2026-03-13 22:13:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
383	2026-03-14 05:41:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
384	2026-03-15 05:33:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
385	2026-03-15 05:26:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
386	2026-03-15 00:42:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
387	2026-03-15 03:11:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
388	2026-03-14 19:40:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
389	2026-03-15 03:08:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
390	2026-03-15 04:06:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
391	2026-03-14 23:54:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
392	2026-03-14 19:51:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
393	2026-03-15 04:46:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
394	2026-03-15 05:18:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
395	2026-03-14 20:27:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
396	2026-03-15 20:03:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
397	2026-03-16 05:10:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
398	2026-03-15 23:12:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
399	2026-03-16 04:47:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
400	2026-03-15 22:58:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
401	2026-03-15 20:04:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
402	2026-03-16 05:35:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
403	2026-03-16 05:29:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
404	2026-03-16 04:05:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
405	2026-03-16 01:39:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
406	2026-03-17 00:26:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
407	2026-03-17 04:25:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
408	2026-03-17 01:24:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
409	2026-03-17 03:49:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
410	2026-03-17 03:17:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
411	2026-03-17 03:16:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-66	f
412	2026-03-17 00:10:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
413	2026-03-17 23:35:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
414	2026-03-18 02:21:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
415	2026-03-18 00:50:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
416	2026-03-17 21:49:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
417	2026-03-17 19:18:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
418	2026-03-18 20:11:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
419	2026-03-19 04:55:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
420	2026-03-18 23:50:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
421	2026-03-18 19:54:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
422	2026-03-19 00:10:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
423	2026-03-18 19:13:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
424	2026-03-19 04:25:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
425	2026-03-18 19:26:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
426	2026-03-18 22:16:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
427	2026-03-19 20:22:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
428	2026-03-20 04:22:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
429	2026-03-19 23:43:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
430	2026-03-20 00:47:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
431	2026-03-20 02:59:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
432	2026-03-20 03:49:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
433	2026-03-19 23:55:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
434	2026-03-20 01:16:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
435	2026-03-20 05:46:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
436	2026-03-19 23:40:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
437	2026-03-21 04:44:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
438	2026-03-20 21:57:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
439	2026-03-21 05:42:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
440	2026-03-21 05:24:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
441	2026-03-21 04:05:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
442	2026-03-21 02:23:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
443	2026-03-21 03:52:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
444	2026-03-21 05:21:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
445	2026-03-21 04:26:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
446	2026-03-21 01:43:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
447	2026-03-21 01:30:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
448	2026-03-22 03:38:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
449	2026-03-21 23:35:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
450	2026-03-22 03:20:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
451	2026-03-22 04:29:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
452	2026-03-21 20:28:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
453	2026-03-21 21:17:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
454	2026-03-21 22:41:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
455	2026-03-22 02:28:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
456	2026-03-22 01:02:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
457	2026-03-22 05:12:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
458	2026-03-22 01:43:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
459	2026-03-22 23:23:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
460	2026-03-23 02:08:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
461	2026-03-23 05:06:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
462	2026-03-22 20:36:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
463	2026-03-22 22:11:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
464	2026-03-22 23:38:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
465	2026-03-22 19:14:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
466	2026-03-23 05:19:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
467	2026-03-22 21:37:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
468	2026-03-22 22:55:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
469	2026-03-22 22:38:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
470	2026-03-22 22:27:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
471	2026-03-23 02:25:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
472	2026-03-23 03:45:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
473	2026-03-23 02:48:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
474	2026-03-23 23:10:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
475	2026-03-24 04:33:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
476	2026-03-23 23:11:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
477	2026-03-23 19:37:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
478	2026-03-23 20:23:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
479	2026-03-23 22:28:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
480	2026-03-23 19:13:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
481	2026-03-24 05:10:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
482	2026-03-24 04:44:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
483	2026-03-23 20:58:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
484	2026-03-23 19:31:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
485	2026-03-25 01:16:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
486	2026-03-24 19:40:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
487	2026-03-25 04:18:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
488	2026-03-24 22:56:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
489	2026-03-24 19:01:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
490	2026-03-24 19:49:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
491	2026-03-24 20:13:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
492	2026-03-25 02:51:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
493	2026-03-25 00:43:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
494	2026-03-25 02:46:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
495	2026-03-25 01:49:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
496	2026-03-26 02:33:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
497	2026-03-26 00:59:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
498	2026-03-25 23:43:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
499	2026-03-26 01:56:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
500	2026-03-25 22:56:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
501	2026-03-27 03:28:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
502	2026-03-27 01:24:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
503	2026-03-27 05:40:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
504	2026-03-26 23:23:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
505	2026-03-27 05:24:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
506	2026-03-27 02:52:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
507	2026-03-26 20:29:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
508	2026-03-26 23:19:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
509	2026-03-27 02:05:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
510	2026-03-27 02:08:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
511	2026-03-27 05:42:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
512	2026-03-27 19:30:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
513	2026-03-28 04:34:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
514	2026-03-28 03:15:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
515	2026-03-28 04:07:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
516	2026-03-28 02:45:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
517	2026-03-28 04:29:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
518	2026-03-27 22:34:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
519	2026-03-28 02:09:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
520	2026-03-28 05:05:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
521	2026-03-28 03:17:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
522	2026-03-28 05:28:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-67	f
523	2026-03-27 21:28:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
524	2026-03-28 20:57:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
525	2026-03-29 04:38:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
526	2026-03-29 00:16:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
527	2026-03-29 01:19:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
528	2026-03-28 21:42:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
529	2026-03-28 18:55:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
530	2026-03-29 01:59:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
531	2026-03-29 22:25:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
532	2026-03-29 20:39:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
533	2026-03-30 00:03:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
534	2026-03-29 21:16:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
535	2026-03-29 21:29:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
536	2026-03-29 19:52:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
537	2026-03-30 00:19:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
538	2026-03-29 21:12:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
539	2026-03-29 18:53:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
540	2026-03-29 20:06:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
541	2026-03-30 00:19:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
542	2026-03-29 21:57:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
543	2026-03-30 03:29:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
544	2026-03-31 03:24:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
545	2026-03-30 19:32:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
546	2026-03-31 04:49:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
547	2026-03-31 01:10:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
548	2026-03-30 19:30:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
549	2026-03-30 22:10:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
550	2026-03-31 05:29:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
551	2026-03-30 21:57:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
552	2026-03-31 03:35:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
553	2026-03-31 03:03:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
554	2026-03-31 05:38:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
555	2026-03-31 22:33:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
556	2026-04-01 03:14:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
557	2026-04-01 03:45:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
558	2026-04-01 00:34:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
559	2026-03-31 20:59:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
560	2026-04-01 03:56:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
561	2026-04-01 01:03:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
562	2026-04-01 00:24:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
563	2026-04-01 01:26:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
564	2026-04-01 02:46:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
565	2026-04-01 01:19:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
566	2026-04-01 05:13:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
567	2026-03-31 20:51:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
568	2026-03-31 23:55:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
569	2026-04-01 23:08:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
570	2026-04-01 23:25:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
571	2026-04-02 01:33:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
572	2026-04-02 05:37:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
573	2026-04-01 20:37:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
574	2026-04-02 01:41:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
575	2026-04-02 00:45:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
576	2026-04-02 20:41:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
577	2026-04-03 05:46:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
578	2026-04-02 21:52:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
579	2026-04-02 20:54:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
580	2026-04-02 22:37:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
581	2026-04-03 05:00:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
582	2026-04-02 23:01:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
583	2026-04-02 18:56:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
584	2026-04-03 02:12:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
585	2026-04-04 00:58:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
586	2026-04-04 04:56:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
587	2026-04-04 05:06:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
588	2026-04-04 01:13:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
589	2026-04-03 22:45:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
590	2026-04-03 20:14:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
591	2026-04-04 00:27:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
592	2026-04-04 05:13:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
593	2026-04-03 21:20:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
594	2026-04-04 00:01:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
595	2026-04-03 19:22:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
596	2026-04-04 03:14:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
597	2026-04-05 02:12:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
598	2026-04-04 19:19:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
599	2026-04-05 01:33:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
600	2026-04-05 01:49:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
601	2026-04-05 02:30:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
602	2026-04-05 00:51:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
603	2026-04-04 19:18:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
604	2026-04-05 03:49:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
605	2026-04-04 20:40:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
606	2026-04-04 22:09:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
607	2026-04-04 21:44:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
608	2026-04-05 05:23:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
609	2026-04-05 03:01:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
610	2026-04-04 19:05:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
611	2026-04-06 00:07:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
612	2026-04-06 00:30:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
613	2026-04-05 19:06:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
614	2026-04-06 01:28:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
615	2026-04-06 05:44:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
616	2026-04-05 23:06:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
617	2026-04-06 03:39:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
618	2026-04-05 22:58:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
619	2026-04-06 19:50:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
620	2026-04-06 20:10:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
621	2026-04-07 00:03:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
622	2026-04-06 21:44:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
623	2026-04-07 04:31:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
624	2026-04-06 19:15:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
625	2026-04-06 23:47:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
626	2026-04-06 23:11:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
627	2026-04-06 22:11:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-68	f
628	2026-04-07 02:50:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
629	2026-04-06 23:46:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
630	2026-04-07 22:05:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
631	2026-04-07 21:22:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
632	2026-04-07 22:13:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
633	2026-04-07 22:26:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
634	2026-04-08 03:36:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
635	2026-04-07 21:31:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
636	2026-04-07 20:55:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
637	2026-04-07 22:22:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
638	2026-04-07 21:50:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
639	2026-04-09 01:18:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
640	2026-04-08 23:53:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
641	2026-04-08 21:25:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
642	2026-04-09 01:05:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
643	2026-04-08 21:06:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
644	2026-04-08 19:02:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
645	2026-04-09 03:54:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
646	2026-04-10 03:06:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
647	2026-04-09 23:54:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
648	2026-04-09 20:09:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
649	2026-04-10 01:51:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
650	2026-04-09 23:46:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
651	2026-04-10 04:14:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
652	2026-04-11 03:54:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
653	2026-04-10 22:45:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
654	2026-04-10 20:10:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
655	2026-04-10 23:42:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
656	2026-04-10 22:18:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
657	2026-04-10 23:30:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
658	2026-04-11 19:55:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
659	2026-04-11 20:27:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
660	2026-04-11 23:27:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
661	2026-04-12 00:37:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
662	2026-04-11 21:38:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
663	2026-04-12 01:51:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
664	2026-04-12 04:41:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
665	2026-04-11 19:48:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
666	2026-04-12 01:34:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
667	2026-04-11 22:57:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
668	2026-04-12 00:47:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
669	2026-04-12 23:54:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
670	2026-04-13 02:59:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
671	2026-04-13 04:17:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
672	2026-04-13 02:54:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
673	2026-04-13 00:05:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
674	2026-04-12 21:01:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
675	2026-04-13 00:45:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
676	2026-04-12 22:50:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
677	2026-04-13 05:51:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
678	2026-04-13 04:05:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
679	2026-04-13 19:03:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
680	2026-04-14 04:24:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
681	2026-04-13 21:25:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
682	2026-04-13 23:47:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
683	2026-04-14 01:19:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
684	2026-04-14 02:15:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
685	2026-04-13 19:19:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
686	2026-04-13 23:13:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
687	2026-04-14 04:34:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
688	2026-04-13 19:36:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
689	2026-04-14 05:17:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
690	2026-04-14 19:14:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
691	2026-04-15 00:33:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
692	2026-04-15 01:18:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
693	2026-04-14 20:02:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
694	2026-04-14 21:37:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
695	2026-04-14 20:51:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
696	2026-04-14 20:10:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
697	2026-04-14 19:37:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
698	2026-04-14 22:29:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
699	2026-04-15 05:39:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
700	2026-04-15 03:43:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
701	2026-04-15 00:58:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
702	2026-04-15 04:48:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
703	2026-04-14 22:28:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
704	2026-04-16 01:40:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
705	2026-04-16 04:33:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
706	2026-04-16 00:40:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
707	2026-04-15 19:34:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
708	2026-04-15 19:47:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
709	2026-04-16 02:59:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
710	2026-04-15 20:35:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
711	2026-04-16 04:43:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
712	2026-04-16 00:36:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
713	2026-04-16 01:57:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
714	2026-04-16 20:47:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
715	2026-04-17 01:03:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
716	2026-04-17 00:39:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
717	2026-04-17 01:45:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
718	2026-04-17 04:07:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-69	f
719	2026-04-16 20:32:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
720	2026-04-18 04:23:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
721	2026-04-18 04:06:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
722	2026-04-18 00:43:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
723	2026-04-17 21:01:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
724	2026-04-17 20:27:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
725	2026-04-18 05:15:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
726	2026-04-17 19:45:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
727	2026-04-19 03:21:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
728	2026-04-18 21:14:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
729	2026-04-19 04:10:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
730	2026-04-19 02:56:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
731	2026-04-19 02:38:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
732	2026-04-19 00:25:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
733	2026-04-18 19:56:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
734	2026-04-19 03:40:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
735	2026-04-18 22:48:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
736	2026-04-18 20:01:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
737	2026-04-19 03:12:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
738	2026-04-19 02:30:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
739	2026-04-19 03:25:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
740	2026-04-19 04:34:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
741	2026-04-19 19:31:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
742	2026-04-19 19:09:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
743	2026-04-19 19:19:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
744	2026-04-19 21:08:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
745	2026-04-20 01:12:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
746	2026-04-19 22:47:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
747	2026-04-19 22:15:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
748	2026-04-21 04:38:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
749	2026-04-20 23:25:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
750	2026-04-21 04:30:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
751	2026-04-21 02:43:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
752	2026-04-20 21:36:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
753	2026-04-21 00:32:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
754	2026-04-20 19:21:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
755	2026-04-21 02:21:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
756	2026-04-21 20:03:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
757	2026-04-22 05:37:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
758	2026-04-22 02:29:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
759	2026-04-22 02:54:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
760	2026-04-22 00:36:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
761	2026-04-22 02:07:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
762	2026-04-21 23:56:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
763	2026-04-21 19:13:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
764	2026-04-23 03:23:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
765	2026-04-22 23:26:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
766	2026-04-23 02:53:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
767	2026-04-23 01:05:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
768	2026-04-23 03:36:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
769	2026-04-22 21:25:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
770	2026-04-23 00:17:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
771	2026-04-23 01:40:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
772	2026-04-23 05:04:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
773	2026-04-22 22:00:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
774	2026-04-23 02:57:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
775	2026-04-22 23:02:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
776	2026-04-22 22:51:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
777	2026-04-24 02:14:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
778	2026-04-24 03:48:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
779	2026-04-24 03:42:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
780	2026-04-24 05:21:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
781	2026-04-23 21:04:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
782	2026-04-23 20:24:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
783	2026-04-24 19:41:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
784	2026-04-25 00:30:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
785	2026-04-25 03:27:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
786	2026-04-25 03:54:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
787	2026-04-25 01:49:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
788	2026-04-25 00:02:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
789	2026-04-25 05:39:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
790	2026-04-25 01:13:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
791	2026-04-24 19:04:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
792	2026-04-25 04:52:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
793	2026-04-25 03:52:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
794	2026-04-24 23:12:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
795	2026-04-24 22:26:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
796	2026-04-24 23:07:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
797	2026-04-25 03:21:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
798	2026-04-26 02:56:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
799	2026-04-26 04:29:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
800	2026-04-25 19:11:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
801	2026-04-26 04:29:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
802	2026-04-25 20:23:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
803	2026-04-26 00:43:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
804	2026-04-26 04:06:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
805	2026-04-26 03:27:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
806	2026-04-25 19:52:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
807	2026-04-25 22:22:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
808	2026-04-26 01:03:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
809	2026-04-25 19:29:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
810	2026-04-26 03:40:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
811	2026-04-25 18:53:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
812	2026-04-27 05:33:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
813	2026-04-27 01:36:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
814	2026-04-26 22:39:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
815	2026-04-27 00:16:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
816	2026-04-27 03:24:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
817	2026-04-26 19:18:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
818	2026-04-27 01:29:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
819	2026-04-26 23:13:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
820	2026-04-27 04:52:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
821	2026-04-27 03:41:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
822	2026-04-27 00:51:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-70	f
823	2026-04-26 21:11:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
824	2026-04-28 00:25:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
825	2026-04-28 00:37:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
826	2026-04-28 04:28:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
827	2026-04-27 21:05:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
828	2026-04-28 02:13:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
829	2026-04-28 01:08:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
830	2026-04-27 22:25:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
831	2026-04-27 21:20:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-75	f
832	2026-04-28 22:42:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
833	2026-04-28 23:55:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-75	f
834	2026-04-28 20:07:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
835	2026-04-29 01:35:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
836	2026-04-28 21:36:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
837	2026-04-28 22:50:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
838	2026-04-29 03:43:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
839	2026-04-28 21:30:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
840	2026-04-29 00:37:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
841	2026-04-28 19:06:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
842	2026-04-29 04:02:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
843	2026-04-28 18:58:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-75	f
844	2026-04-28 21:56:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
845	2026-04-28 21:33:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
846	2026-04-29 20:13:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
847	2026-04-30 00:27:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-75	f
848	2026-04-29 23:22:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
849	2026-04-29 19:36:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
850	2026-04-30 01:20:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
851	2026-04-29 20:14:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
852	2026-04-30 04:50:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
853	2026-04-30 19:30:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
854	2026-05-01 00:35:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
855	2026-05-01 03:15:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
856	2026-05-01 00:39:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
857	2026-05-01 02:11:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
858	2026-04-30 20:29:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-75	f
859	2026-05-01 01:30:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
860	2026-05-01 05:46:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-75	f
861	2026-04-30 22:14:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
862	2026-04-30 22:22:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
863	2026-04-30 22:14:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-75	f
864	2026-05-01 01:05:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
865	2026-05-01 00:20:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
866	2026-04-30 22:40:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
867	2026-05-01 22:52:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
868	2026-05-01 22:12:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
869	2026-05-02 05:21:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
870	2026-05-01 19:33:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
871	2026-05-01 22:47:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
872	2026-05-02 03:17:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
873	2026-05-02 05:38:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
874	2026-05-02 00:46:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
875	2026-05-01 21:39:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-75	f
876	2026-05-01 20:46:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
877	2026-05-01 19:34:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
878	2026-05-02 03:40:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-75	f
879	2026-05-01 19:22:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
880	2026-05-02 01:10:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
881	2026-05-02 20:17:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-75	f
882	2026-05-03 02:26:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
883	2026-05-02 22:20:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
884	2026-05-03 04:57:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-75	f
885	2026-05-03 03:54:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
886	2026-05-02 18:56:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
887	2026-05-03 05:20:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
888	2026-05-02 21:34:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
889	2026-05-02 21:50:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
890	2026-05-02 19:26:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
891	2026-05-02 19:27:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-75	f
892	2026-05-03 04:26:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-75	f
893	2026-05-02 22:03:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
894	2026-05-02 23:02:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-75	f
895	2026-05-03 23:40:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
896	2026-05-04 01:45:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
897	2026-05-03 18:53:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
898	2026-05-03 21:09:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
899	2026-05-04 03:26:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
900	2026-05-04 03:10:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
901	2026-05-04 05:50:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
902	2026-05-04 04:51:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
903	2026-05-04 02:47:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
904	2026-05-03 20:55:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-75	f
905	2026-05-03 20:44:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-75	f
906	2026-05-04 01:56:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
907	2026-05-04 05:12:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
908	2026-05-03 23:11:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
909	2026-05-03 19:08:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
910	2026-05-04 22:35:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
911	2026-05-04 19:12:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
912	2026-05-04 22:01:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
913	2026-05-05 00:12:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
914	2026-05-05 04:11:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
915	2026-05-05 03:55:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
916	2026-05-05 03:36:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
917	2026-05-04 22:50:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-75	f
918	2026-05-05 01:17:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-75	f
919	2026-05-05 00:01:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
920	2026-05-04 23:38:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-75	f
921	2026-05-05 05:46:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-75	f
922	2026-05-04 20:29:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
923	2026-05-04 21:59:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
924	2026-05-05 18:58:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
925	2026-05-05 23:22:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
926	2026-05-05 19:35:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
927	2026-05-05 23:49:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
928	2026-05-05 20:19:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-75	f
929	2026-05-05 23:26:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-75	f
930	2026-05-05 21:22:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
931	2026-05-06 01:23:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
932	2026-05-05 19:16:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
933	2026-05-05 19:14:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
934	2026-05-06 04:04:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-75	f
935	2026-05-07 03:09:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
936	2026-05-06 22:12:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
937	2026-05-07 03:42:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-74	f
938	2026-05-07 03:45:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
939	2026-05-06 20:10:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
940	2026-05-07 04:30:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
941	2026-05-07 02:14:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
942	2026-05-06 19:41:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-75	f
943	2026-05-07 05:30:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-73	f
944	2026-05-06 20:19:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-71	f
945	2026-05-06 19:01:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-72	f
946	2026-05-06 19:08:05.135866	PHONE_USER_1	aa:bb:cc:dd:ee:ff	\N	-75	f
\.


--
-- TOC entry 3435 (class 0 OID 0)
-- Dependencies: 214
-- Name: dim_topology_location_id_seq; Type: SEQUENCE SET; Schema: public; Owner: domi
--

SELECT pg_catalog.setval('public.dim_topology_location_id_seq', 2, true);


--
-- TOC entry 3436 (class 0 OID 0)
-- Dependencies: 217
-- Name: fact_telemetry_telemetry_id_seq; Type: SEQUENCE SET; Schema: public; Owner: domi
--

SELECT pg_catalog.setval('public.fact_telemetry_telemetry_id_seq', 946, true);


--
-- TOC entry 3275 (class 2606 OID 24712)
-- Name: dim_iot_devices dim_iot_devices_pkey; Type: CONSTRAINT; Schema: public; Owner: domi
--

ALTER TABLE ONLY public.dim_iot_devices
    ADD CONSTRAINT dim_iot_devices_pkey PRIMARY KEY (mac_address);


--
-- TOC entry 3273 (class 2606 OID 24707)
-- Name: dim_topology dim_topology_pkey; Type: CONSTRAINT; Schema: public; Owner: domi
--

ALTER TABLE ONLY public.dim_topology
    ADD CONSTRAINT dim_topology_pkey PRIMARY KEY (location_id);


--
-- TOC entry 3277 (class 2606 OID 24725)
-- Name: fact_telemetry fact_telemetry_pkey; Type: CONSTRAINT; Schema: public; Owner: domi
--

ALTER TABLE ONLY public.fact_telemetry
    ADD CONSTRAINT fact_telemetry_pkey PRIMARY KEY (telemetry_id);


--
-- TOC entry 3278 (class 1259 OID 24731)
-- Name: idx_telemetry_time_mac; Type: INDEX; Schema: public; Owner: domi
--

CREATE INDEX idx_telemetry_time_mac ON public.fact_telemetry USING btree ("timestamp", mac_address);


--
-- TOC entry 3279 (class 2606 OID 24713)
-- Name: dim_iot_devices dim_iot_devices_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: domi
--

ALTER TABLE ONLY public.dim_iot_devices
    ADD CONSTRAINT dim_iot_devices_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.dim_topology(location_id);


--
-- TOC entry 3280 (class 2606 OID 24726)
-- Name: fact_telemetry fact_telemetry_mac_address_fkey; Type: FK CONSTRAINT; Schema: public; Owner: domi
--

ALTER TABLE ONLY public.fact_telemetry
    ADD CONSTRAINT fact_telemetry_mac_address_fkey FOREIGN KEY (mac_address) REFERENCES public.dim_iot_devices(mac_address);


-- Completed on 2026-05-07 11:13:23

--
-- PostgreSQL database dump complete
--

