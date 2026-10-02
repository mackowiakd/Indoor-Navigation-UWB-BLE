--
-- PostgreSQL database dump
--

-- Dumped from database version 15.17
-- Dumped by pg_dump version 17.0

-- Started on 2026-09-20 11:43:08

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

--
-- TOC entry 4 (class 2615 OID 2200)
-- Name: public; Type: SCHEMA; Schema: -; Owner: pg_database_owner
--

CREATE SCHEMA public;


ALTER SCHEMA public OWNER TO pg_database_owner;

--
-- TOC entry 3462 (class 0 OID 0)
-- Dependencies: 4
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: pg_database_owner
--

COMMENT ON SCHEMA public IS 'standard public schema';


--
-- TOC entry 232 (class 1255 OID 24763)
-- Name: sync_target_location(); Type: FUNCTION; Schema: public; Owner: domi
--

CREATE FUNCTION public.sync_target_location() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    -- Sprawdzamy, czy cel ma przypisany sprzęt (czyli czy jest celem Mikro)
    IF NEW.Associated_MAC IS NOT NULL THEN
        -- Pobieramy prawdziwą lokalizację urządzenia i wpisujemy ją do naszego celu
        SELECT Location_ID INTO NEW.Location_ID
        FROM Dim_IoT_Devices
        WHERE MAC_Address = NEW.Associated_MAC;
    END IF;
    
    RETURN NEW;
END;
$$;


ALTER FUNCTION public.sync_target_location() OWNER TO domi;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- TOC entry 217 (class 1259 OID 24708)
-- Name: dim_iot_devices; Type: TABLE; Schema: public; Owner: domi
--

CREATE TABLE public.dim_iot_devices (
    mac_address character varying(17) NOT NULL,
    device_type character varying(20) NOT NULL,
    location_id integer,
    semantic_role character varying(100),
    tx_power_config integer,
    global_x double precision,
    global_y double precision,
    global_z double precision
);


ALTER TABLE public.dim_iot_devices OWNER TO domi;

--
-- TOC entry 219 (class 1259 OID 24746)
-- Name: dim_navigation_targets; Type: TABLE; Schema: public; Owner: domi
--

CREATE TABLE public.dim_navigation_targets (
    target_id integer NOT NULL,
    location_id integer,
    name character varying(100) NOT NULL,
    category character varying(50),
    associated_mac character varying(17),
    is_macro_target boolean DEFAULT false
);


ALTER TABLE public.dim_navigation_targets OWNER TO domi;

--
-- TOC entry 218 (class 1259 OID 24745)
-- Name: dim_navigation_targets_target_id_seq; Type: SEQUENCE; Schema: public; Owner: domi
--

CREATE SEQUENCE public.dim_navigation_targets_target_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.dim_navigation_targets_target_id_seq OWNER TO domi;

--
-- TOC entry 3463 (class 0 OID 0)
-- Dependencies: 218
-- Name: dim_navigation_targets_target_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: domi
--

ALTER SEQUENCE public.dim_navigation_targets_target_id_seq OWNED BY public.dim_navigation_targets.target_id;


--
-- TOC entry 216 (class 1259 OID 24702)
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
-- TOC entry 215 (class 1259 OID 24701)
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
-- TOC entry 3464 (class 0 OID 0)
-- Dependencies: 215
-- Name: dim_topology_location_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: domi
--

ALTER SEQUENCE public.dim_topology_location_id_seq OWNED BY public.dim_topology.location_id;


--
-- TOC entry 3298 (class 2604 OID 24749)
-- Name: dim_navigation_targets target_id; Type: DEFAULT; Schema: public; Owner: domi
--

ALTER TABLE ONLY public.dim_navigation_targets ALTER COLUMN target_id SET DEFAULT nextval('public.dim_navigation_targets_target_id_seq'::regclass);


--
-- TOC entry 3297 (class 2604 OID 24705)
-- Name: dim_topology location_id; Type: DEFAULT; Schema: public; Owner: domi
--

ALTER TABLE ONLY public.dim_topology ALTER COLUMN location_id SET DEFAULT nextval('public.dim_topology_location_id_seq'::regclass);


--
-- TOC entry 3454 (class 0 OID 24708)
-- Dependencies: 217
-- Data for Name: dim_iot_devices; Type: TABLE DATA; Schema: public; Owner: domi
--

COPY public.dim_iot_devices (mac_address, device_type, location_id, semantic_role, tx_power_config, global_x, global_y, global_z) FROM stdin;
ff:ff:12:b1:64:d1	BLE_BEACON	1	Drzwi Wejściowe (Od zewnątrz)	-59	\N	\N	\N
a8:03:2a:b8:ee:fa	BLE_BEACON	2	Okno	-59	\N	\N	\N
ff:ff:12:8d:7c:df	BLE_BEACON	2	Ekspres / Kubek	-59	\N	\N	\N
0x0002	UWB_ANCHOR	2	Kotwica UWB - Narożnik Prawy	\N	0	0	\N
0x0001	UWB_ANCHOR	2	Kotwica UWB - Narożnik Lewy	\N	0	3	\N
ff:ff:12:a2:43:90	BLE_BEACON	2	Biurko z laptopem	-59	2	1.5	\N
\.


--
-- TOC entry 3456 (class 0 OID 24746)
-- Dependencies: 219
-- Data for Name: dim_navigation_targets; Type: TABLE DATA; Schema: public; Owner: domi
--

COPY public.dim_navigation_targets (target_id, location_id, name, category, associated_mac, is_macro_target) FROM stdin;
1	1	Idź do: Przedpokój	ZONE	\N	t
2	2	Idź do: Laboratorium UWB	ZONE	\N	t
3	2	Precyzyjnie: Okno	EQUIPMENT	a8:03:2a:b8:ee:fa	f
4	2	Precyzyjnie: Ekspres	EQUIPMENT	ff:ff:12:8d:7c:df	f
5	2	Precyzyjnie: Biurko	EQUIPMENT	ff:ff:12:a2:43:90	f
6	1	Precyzyjnie: Drzwi	EQUIPMENT	ff:ff:12:b1:64:d1	f
\.


--
-- TOC entry 3453 (class 0 OID 24702)
-- Dependencies: 216
-- Data for Name: dim_topology; Type: TABLE DATA; Schema: public; Owner: domi
--

COPY public.dim_topology (location_id, building, wing, floor, room_name) FROM stdin;
1	Dom	Przedpokój	1	Strefa Startowa
2	Dom	Pokój	1	Laboratorium UWB
\.


--
-- TOC entry 3465 (class 0 OID 0)
-- Dependencies: 218
-- Name: dim_navigation_targets_target_id_seq; Type: SEQUENCE SET; Schema: public; Owner: domi
--

SELECT pg_catalog.setval('public.dim_navigation_targets_target_id_seq', 6, true);


--
-- TOC entry 3466 (class 0 OID 0)
-- Dependencies: 215
-- Name: dim_topology_location_id_seq; Type: SEQUENCE SET; Schema: public; Owner: domi
--

SELECT pg_catalog.setval('public.dim_topology_location_id_seq', 2, true);


--
-- TOC entry 3303 (class 2606 OID 24712)
-- Name: dim_iot_devices dim_iot_devices_pkey; Type: CONSTRAINT; Schema: public; Owner: domi
--

ALTER TABLE ONLY public.dim_iot_devices
    ADD CONSTRAINT dim_iot_devices_pkey PRIMARY KEY (mac_address);


--
-- TOC entry 3305 (class 2606 OID 24752)
-- Name: dim_navigation_targets dim_navigation_targets_pkey; Type: CONSTRAINT; Schema: public; Owner: domi
--

ALTER TABLE ONLY public.dim_navigation_targets
    ADD CONSTRAINT dim_navigation_targets_pkey PRIMARY KEY (target_id);


--
-- TOC entry 3301 (class 2606 OID 24707)
-- Name: dim_topology dim_topology_pkey; Type: CONSTRAINT; Schema: public; Owner: domi
--

ALTER TABLE ONLY public.dim_topology
    ADD CONSTRAINT dim_topology_pkey PRIMARY KEY (location_id);


--
-- TOC entry 3309 (class 2620 OID 24764)
-- Name: dim_navigation_targets trg_sync_target_location; Type: TRIGGER; Schema: public; Owner: domi
--

CREATE TRIGGER trg_sync_target_location BEFORE INSERT OR UPDATE ON public.dim_navigation_targets FOR EACH ROW EXECUTE FUNCTION public.sync_target_location();


--
-- TOC entry 3306 (class 2606 OID 24713)
-- Name: dim_iot_devices dim_iot_devices_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: domi
--

ALTER TABLE ONLY public.dim_iot_devices
    ADD CONSTRAINT dim_iot_devices_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.dim_topology(location_id);


--
-- TOC entry 3307 (class 2606 OID 24758)
-- Name: dim_navigation_targets dim_navigation_targets_associated_mac_fkey; Type: FK CONSTRAINT; Schema: public; Owner: domi
--

ALTER TABLE ONLY public.dim_navigation_targets
    ADD CONSTRAINT dim_navigation_targets_associated_mac_fkey FOREIGN KEY (associated_mac) REFERENCES public.dim_iot_devices(mac_address);


--
-- TOC entry 3308 (class 2606 OID 24753)
-- Name: dim_navigation_targets dim_navigation_targets_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: domi
--

ALTER TABLE ONLY public.dim_navigation_targets
    ADD CONSTRAINT dim_navigation_targets_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.dim_topology(location_id);


-- Completed on 2026-09-20 11:43:08

--
-- PostgreSQL database dump complete
--

