#include "common.h"

namespace fs = std::filesystem;

namespace platf {
    void adjust_thread_priority(thread_priority_e priority) {
        // TODO
    }
    fs::path appdata() {
        // TODO
        return {};
    }
    std::string get_host_name() {
        // TODO
        return "Mirror";
    }
    std::vector<supported_gamepad_t> gamepads_list {
    };
    std::vector<supported_gamepad_t> &supported_gamepads(input_t *input) {
        // TODO
        return gamepads_list;
    }
    std::string get_mac_address(const std::string_view &address) {
        // TODO
        return {};
    }
    struct client_input_raw_t: public client_input_t {
    };
    std::unique_ptr<client_input_t> allocate_client_input_context(input_t &input) {
        return std::make_unique<client_input_raw_t>();
    }
    void freeInput(void *p) {
    }

  platform_caps::caps_t get_capabilities() {
    platform_caps::caps_t caps = 0;
    // TODO: if has_uinput
    caps |= platform_caps::pen_touch;

    // We support controller touchpad input only when emulating the PS5 controller
    if (config::input.gamepad == "ds5"sv || config::input.gamepad == "auto"sv) {
      caps |= platform_caps::controller_touch;
    }

    return caps;
  }
    std::string from_sockaddr(const sockaddr *const ip_addr) {
        char data[INET6_ADDRSTRLEN] = {};

        auto family = ip_addr->sa_family;
        if (family == AF_INET6) {
            inet_ntop(AF_INET6, &((sockaddr_in6 *) ip_addr)->sin6_addr, data, INET6_ADDRSTRLEN);
        } else if (family == AF_INET) {
            inet_ntop(AF_INET, &((sockaddr_in *) ip_addr)->sin_addr, data, INET_ADDRSTRLEN);
        }

        return std::string {data};
    }
    std::pair<std::uint16_t, std::string> from_sockaddr_ex(const sockaddr *const ip_addr) {
        char data[INET6_ADDRSTRLEN] = {};

        auto family = ip_addr->sa_family;
        std::uint16_t port = 0;
        if (family == AF_INET6) {
            inet_ntop(AF_INET6, &((sockaddr_in6 *) ip_addr)->sin6_addr, data, INET6_ADDRSTRLEN);
            port = ((sockaddr_in6 *) ip_addr)->sin6_port;
        } else if (family == AF_INET) {
            inet_ntop(AF_INET, &((sockaddr_in *) ip_addr)->sin_addr, data, INET_ADDRSTRLEN);
            port = ((sockaddr_in *) ip_addr)->sin_port;
        }

        return {port, std::string{data}};
    }
    class linux_high_precision_timer: public high_precision_timer {
    public:
        void sleep_for(const std::chrono::nanoseconds &duration) override {
            std::this_thread::sleep_for(duration);
        }

        operator bool() override {
            return true;
        }
    };

    std::unique_ptr<high_precision_timer> create_high_precision_timer() {
        return std::make_unique<linux_high_precision_timer>();
    }

    struct sockaddr_in to_sockaddr(boost::asio::ip::address_v4 address, uint16_t port) {
        struct sockaddr_in saddr_v4 = {};

        saddr_v4.sin_family = AF_INET;
        saddr_v4.sin_port = htons(port);

        auto addr_bytes = address.to_bytes();
        memcpy(&saddr_v4.sin_addr, addr_bytes.data(), sizeof(saddr_v4.sin_addr));

        return saddr_v4;
    }

    struct sockaddr_in6 to_sockaddr(boost::asio::ip::address_v6 address, uint16_t port) {
        struct sockaddr_in6 saddr_v6 = {};

        saddr_v6.sin6_family = AF_INET6;
        saddr_v6.sin6_port = htons(port);
        saddr_v6.sin6_scope_id = address.scope_id();

        auto addr_bytes = address.to_bytes();
        memcpy(&saddr_v6.sin6_addr, addr_bytes.data(), sizeof(saddr_v6.sin6_addr));

        return saddr_v6;
    }


    bool send_batch(batched_send_info_t &send_info) {
        auto sockfd = (int) send_info.native_socket;
        struct msghdr msg = {};

        // Convert the target address into a sockaddr
        struct sockaddr_in taddr_v4 = {};
        struct sockaddr_in6 taddr_v6 = {};
        if (send_info.target_address.is_v6()) {
            taddr_v6 = to_sockaddr(send_info.target_address.to_v6(), send_info.target_port);

            msg.msg_name = (struct sockaddr *) &taddr_v6;
            msg.msg_namelen = sizeof(taddr_v6);
        } else {
            taddr_v4 = to_sockaddr(send_info.target_address.to_v4(), send_info.target_port);

            msg.msg_name = (struct sockaddr *) &taddr_v4;
            msg.msg_namelen = sizeof(taddr_v4);
        }

        union {
            char buf[CMSG_SPACE(sizeof(uint16_t)) + std::max(CMSG_SPACE(sizeof(struct in_pktinfo)), CMSG_SPACE(sizeof(struct in6_pktinfo)))];
            struct cmsghdr alignment;
        } cmbuf = {};  // Must be zeroed for CMSG_NXTHDR()

        socklen_t cmbuflen = 0;

        msg.msg_control = cmbuf.buf;
        msg.msg_controllen = sizeof(cmbuf.buf);

        // The PKTINFO option will always be first, then we will conditionally
        // append the UDP_SEGMENT option next if applicable.
        auto pktinfo_cm = CMSG_FIRSTHDR(&msg);
        if (send_info.source_address.is_v6()) {
            struct in6_pktinfo pktInfo;

            struct sockaddr_in6 saddr_v6 = to_sockaddr(send_info.source_address.to_v6(), 0);
            pktInfo.ipi6_addr = saddr_v6.sin6_addr;
            pktInfo.ipi6_ifindex = 0;

            cmbuflen += CMSG_SPACE(sizeof(pktInfo));

            pktinfo_cm->cmsg_level = IPPROTO_IPV6;
            pktinfo_cm->cmsg_type = IPV6_PKTINFO;
            pktinfo_cm->cmsg_len = CMSG_LEN(sizeof(pktInfo));
            memcpy(CMSG_DATA(pktinfo_cm), &pktInfo, sizeof(pktInfo));
        } else {
            struct in_pktinfo pktInfo;

            struct sockaddr_in saddr_v4 = to_sockaddr(send_info.source_address.to_v4(), 0);
            pktInfo.ipi_spec_dst = saddr_v4.sin_addr;
            pktInfo.ipi_ifindex = 0;

            cmbuflen += CMSG_SPACE(sizeof(pktInfo));

            pktinfo_cm->cmsg_level = IPPROTO_IP;
            pktinfo_cm->cmsg_type = IP_PKTINFO;
            pktinfo_cm->cmsg_len = CMSG_LEN(sizeof(pktInfo));
            memcpy(CMSG_DATA(pktinfo_cm), &pktInfo, sizeof(pktInfo));
        }

        auto const max_iovs_per_msg = send_info.payload_buffers.size() + (send_info.headers ? 1 : 0);

#ifdef UDP_SEGMENT
        {
      // UDP GSO on Linux currently only supports sending 64K or 64 segments at a time
      size_t seg_index = 0;
      const size_t seg_max = 65536 / 1500;
      struct iovec iovs[(send_info.headers ? std::min(seg_max, send_info.block_count) : 1) * max_iovs_per_msg] = {};
      auto msg_size = send_info.header_size + send_info.payload_size;
      while (seg_index < send_info.block_count) {
        int iovlen = 0;
        auto segs_in_batch = std::min(send_info.block_count - seg_index, seg_max);
        if (send_info.headers) {
          // Interleave iovs for headers and payloads
          for (auto i = 0; i < segs_in_batch; i++) {
            iovs[iovlen].iov_base = (void *) &send_info.headers[(send_info.block_offset + seg_index + i) * send_info.header_size];
            iovs[iovlen].iov_len = send_info.header_size;
            iovlen++;
            auto payload_desc = send_info.buffer_for_payload_offset((send_info.block_offset + seg_index + i) * send_info.payload_size);
            iovs[iovlen].iov_base = (void *) payload_desc.buffer;
            iovs[iovlen].iov_len = send_info.payload_size;
            iovlen++;
          }
        } else {
          // Translate buffer descriptors into iovs
          auto payload_offset = (send_info.block_offset + seg_index) * send_info.payload_size;
          auto payload_length = payload_offset + (segs_in_batch * send_info.payload_size);
          while (payload_offset < payload_length) {
            auto payload_desc = send_info.buffer_for_payload_offset(payload_offset);
            iovs[iovlen].iov_base = (void *) payload_desc.buffer;
            iovs[iovlen].iov_len = std::min(payload_desc.size, payload_length - payload_offset);
            payload_offset += iovs[iovlen].iov_len;
            iovlen++;
          }
        }

        msg.msg_iov = iovs;
        msg.msg_iovlen = iovlen;

        // We should not use GSO if the data is <= one full block size
        if (segs_in_batch > 1) {
          msg.msg_controllen = cmbuflen + CMSG_SPACE(sizeof(uint16_t));

          // Enable GSO to perform segmentation of our buffer for us
          auto cm = CMSG_NXTHDR(&msg, pktinfo_cm);
          cm->cmsg_level = SOL_UDP;
          cm->cmsg_type = UDP_SEGMENT;
          cm->cmsg_len = CMSG_LEN(sizeof(uint16_t));
          *((uint16_t *) CMSG_DATA(cm)) = msg_size;
        } else {
          msg.msg_controllen = cmbuflen;
        }

        // This will fail if GSO is not available, so we will fall back to non-GSO if
        // it's the first sendmsg() call. On subsequent calls, we will treat errors as
        // actual failures and return to the caller.
        auto bytes_sent = sendmsg(sockfd, &msg, 0);
        if (bytes_sent < 0) {
          // If there's no send buffer space, wait for some to be available
          if (errno == EAGAIN) {
            struct pollfd pfd;

            pfd.fd = sockfd;
            pfd.events = POLLOUT;

            if (poll(&pfd, 1, -1) != 1) {
              BOOST_LOG(warning) << "poll() failed: "sv << errno;
              break;
            }

            // Try to send again
            continue;
          }

          BOOST_LOG(verbose) << "sendmsg() failed: "sv << errno;
          break;
        }

        seg_index += bytes_sent / msg_size;
      }

      // If we sent something, return the status and don't fall back to the non-GSO path.
      if (seg_index != 0) {
        return seg_index >= send_info.block_count;
      }
    }
#endif

        {
            // If GSO is not supported, use sendmmsg() instead.
            struct mmsghdr msgs[send_info.block_count];
            struct iovec iovs[send_info.block_count * (send_info.headers ? 2 : 1)];

            // Zero-initialize arrays
            memset(msgs, 0, sizeof(msgs));
            memset(iovs, 0, sizeof(iovs));

            int iov_idx = 0;
            for (size_t i = 0; i < send_info.block_count; i++) {
                msgs[i].msg_hdr.msg_iov = &iovs[iov_idx];
                msgs[i].msg_hdr.msg_iovlen = send_info.headers ? 2 : 1;

                if (send_info.headers) {
                    iovs[iov_idx].iov_base = (void *) &send_info.headers[(send_info.block_offset + i) * send_info.header_size];
                    iovs[iov_idx].iov_len = send_info.header_size;
                    iov_idx++;
                }
                auto payload_desc = send_info.buffer_for_payload_offset((send_info.block_offset + i) * send_info.payload_size);
                iovs[iov_idx].iov_base = (void *) payload_desc.buffer;
                iovs[iov_idx].iov_len = send_info.payload_size;
                iov_idx++;

                msgs[i].msg_hdr.msg_name = msg.msg_name;
                msgs[i].msg_hdr.msg_namelen = msg.msg_namelen;
                msgs[i].msg_hdr.msg_control = cmbuf.buf;
                msgs[i].msg_hdr.msg_controllen = cmbuflen;
            }

            // Call sendmmsg() until all messages are sent
            size_t blocks_sent = 0;
            while (blocks_sent < send_info.block_count) {
                int msgs_sent = sendmmsg(sockfd, &msgs[blocks_sent], send_info.block_count - blocks_sent, 0);
                if (msgs_sent < 0) {
                    // If there's no send buffer space, wait for some to be available
                    if (errno == EAGAIN) {
                        struct pollfd pfd;

                        pfd.fd = sockfd;
                        pfd.events = POLLOUT;

                        if (poll(&pfd, 1, -1) != 1) {
                            BOOST_LOG(warning) << "poll() failed: "sv << errno;
                            break;
                        }

                        // Try to send again
                        continue;
                    }

                    BOOST_LOG(warning) << "sendmmsg() failed: "sv << errno;
                    return false;
                }

                blocks_sent += msgs_sent;
            }

            return true;
        }
    }

    // We can't track QoS state separately for each destination on this OS,
    // so we keep a ref count to only disable QoS options when all clients
    // are disconnected.
    static std::atomic<int> qos_ref_count = 0;

    class qos_t: public deinit_t {
    public:
        qos_t(int sockfd, std::vector<std::tuple<int, int, int>> options):
                sockfd(sockfd),
                options(options) {
            qos_ref_count++;
        }

        virtual ~qos_t() {
            if (--qos_ref_count == 0) {
                for (const auto &tuple : options) {
                    auto reset_val = std::get<2>(tuple);
                    if (setsockopt(sockfd, std::get<0>(tuple), std::get<1>(tuple), &reset_val, sizeof(reset_val)) < 0) {
                        BOOST_LOG(warning) << "Failed to reset option: "sv << errno;
                    }
                }
            }
        }

    private:
        int sockfd;
        std::vector<std::tuple<int, int, int>> options;
    };

    /**
     * @brief Enables QoS on the given socket for traffic to the specified destination.
     * @param native_socket The native socket handle.
     * @param address The destination address for traffic sent on this socket.
     * @param port The destination port for traffic sent on this socket.
     * @param data_type The type of traffic sent on this socket.
     * @param dscp_tagging Specifies whether to enable DSCP tagging on outgoing traffic.
     */
    std::unique_ptr<deinit_t> enable_socket_qos(uintptr_t native_socket, boost::asio::ip::address &address, uint16_t port, qos_data_type_e data_type, bool dscp_tagging) {
        int sockfd = (int) native_socket;
        std::vector<std::tuple<int, int, int>> reset_options;

        if (dscp_tagging) {
            int level;
            int option;

            // With dual-stack sockets, Linux uses IPV6_TCLASS for IPv6 traffic
            // and IP_TOS for IPv4 traffic.
            if (address.is_v6() && !address.to_v6().is_v4_mapped()) {
                level = SOL_IPV6;
                option = IPV6_TCLASS;
            } else {
                level = SOL_IP;
                option = IP_TOS;
            }

            // The specific DSCP values here are chosen to be consistent with Windows,
            // except that we use CS6 instead of CS7 for audio traffic.
            int dscp = 0;
            switch (data_type) {
                case qos_data_type_e::video:
                    dscp = 40;
                    break;
                case qos_data_type_e::audio:
                    dscp = 48;
                    break;
                default:
                    BOOST_LOG(error) << "Unknown traffic type: "sv << (int) data_type;
                    break;
            }

            if (dscp) {
                // Shift to put the DSCP value in the correct position in the TOS field
                dscp <<= 2;

                if (setsockopt(sockfd, level, option, &dscp, sizeof(dscp)) == 0) {
                    // Reset TOS to -1 when QoS is disabled
                    reset_options.emplace_back(std::make_tuple(level, option, -1));
                } else {
                    BOOST_LOG(error) << "Failed to set TOS/TCLASS: "sv << errno;
                }
            }
        }

        // We can use SO_PRIORITY to set outgoing traffic priority without DSCP tagging.
        //
        // NB: We set this after IP_TOS/IPV6_TCLASS since setting TOS value seems to
        // reset SO_PRIORITY back to 0.
        //
        // 6 is the highest priority that can be used without SYS_CAP_ADMIN.
        int priority = data_type == qos_data_type_e::audio ? 6 : 5;
        if (setsockopt(sockfd, SOL_SOCKET, SO_PRIORITY, &priority, sizeof(priority)) == 0) {
            // Reset SO_PRIORITY to 0 when QoS is disabled
            reset_options.emplace_back(std::make_tuple(SOL_SOCKET, SO_PRIORITY, 0));
        } else {
            BOOST_LOG(error) << "Failed to set SO_PRIORITY: "sv << errno;
        }

        return std::make_unique<qos_t>(sockfd, reset_options);
    }


    bool send(send_info_t &send_info) {
        auto sockfd = (int) send_info.native_socket;
        struct msghdr msg = {};

        // Convert the target address into a sockaddr
        struct sockaddr_in taddr_v4 = {};
        struct sockaddr_in6 taddr_v6 = {};
        if (send_info.target_address.is_v6()) {
            taddr_v6 = to_sockaddr(send_info.target_address.to_v6(), send_info.target_port);

            msg.msg_name = (struct sockaddr *) &taddr_v6;
            msg.msg_namelen = sizeof(taddr_v6);
        } else {
            taddr_v4 = to_sockaddr(send_info.target_address.to_v4(), send_info.target_port);

            msg.msg_name = (struct sockaddr *) &taddr_v4;
            msg.msg_namelen = sizeof(taddr_v4);
        }

        union {
            char buf[std::max(CMSG_SPACE(sizeof(struct in_pktinfo)), CMSG_SPACE(sizeof(struct in6_pktinfo)))];
            struct cmsghdr alignment;
        } cmbuf;

        socklen_t cmbuflen = 0;

        msg.msg_control = cmbuf.buf;
        msg.msg_controllen = sizeof(cmbuf.buf);

        auto pktinfo_cm = CMSG_FIRSTHDR(&msg);
        if (send_info.source_address.is_v6()) {
            struct in6_pktinfo pktInfo;

            struct sockaddr_in6 saddr_v6 = to_sockaddr(send_info.source_address.to_v6(), 0);
            pktInfo.ipi6_addr = saddr_v6.sin6_addr;
            pktInfo.ipi6_ifindex = 0;

            cmbuflen += CMSG_SPACE(sizeof(pktInfo));

            pktinfo_cm->cmsg_level = IPPROTO_IPV6;
            pktinfo_cm->cmsg_type = IPV6_PKTINFO;
            pktinfo_cm->cmsg_len = CMSG_LEN(sizeof(pktInfo));
            memcpy(CMSG_DATA(pktinfo_cm), &pktInfo, sizeof(pktInfo));
        } else {
            struct in_pktinfo pktInfo;

            struct sockaddr_in saddr_v4 = to_sockaddr(send_info.source_address.to_v4(), 0);
            pktInfo.ipi_spec_dst = saddr_v4.sin_addr;
            pktInfo.ipi_ifindex = 0;

            cmbuflen += CMSG_SPACE(sizeof(pktInfo));

            pktinfo_cm->cmsg_level = IPPROTO_IP;
            pktinfo_cm->cmsg_type = IP_PKTINFO;
            pktinfo_cm->cmsg_len = CMSG_LEN(sizeof(pktInfo));
            memcpy(CMSG_DATA(pktinfo_cm), &pktInfo, sizeof(pktInfo));
        }

        struct iovec iovs[2] = {};
        int iovlen = 0;
        if (send_info.header) {
            iovs[iovlen].iov_base = (void *) send_info.header;
            iovs[iovlen].iov_len = send_info.header_size;
            iovlen++;
        }
        iovs[iovlen].iov_base = (void *) send_info.payload;
        iovs[iovlen].iov_len = send_info.payload_size;
        iovlen++;

        msg.msg_iov = iovs;
        msg.msg_iovlen = iovlen;

        msg.msg_controllen = cmbuflen;

        auto bytes_sent = sendmsg(sockfd, &msg, 0);

        // If there's no send buffer space, wait for some to be available
        while (bytes_sent < 0 && errno == EAGAIN) {
            struct pollfd pfd;

            pfd.fd = sockfd;
            pfd.events = POLLOUT;

            if (poll(&pfd, 1, -1) != 1) {
                BOOST_LOG(warning) << "poll() failed: "sv << errno;
                break;
            }

            // Try to send again
            bytes_sent = sendmsg(sockfd, &msg, 0);
        }

        if (bytes_sent < 0) {
            BOOST_LOG(warning) << "sendmsg() failed: "sv << errno;
            return false;
        }

        return true;
    }
}